# Lessons Learned

## 2026-04-03 — SQLCipher WAL Corruption Crash Loop

### The Problem
Users reported repeated crashes (`SQLiteNotADatabaseException: file is not a database, code 26`) multiple times per day. Crash report showed `net.zetetic.database.sqlcipher.SupportHelper.getWritableDatabase` in the stack trace, with `processUptime: 2086 + 337 ms` (crash loop on launch). Device: Pixel 9, Android 16, `userType: full.secondary`.

### Root Cause Analysis
The crash was initially assumed to be passphrase loss (EncryptedSharedPreferences/Keystore corruption), but the stack trace proved otherwise:

- The stack showed `SupportHelper.getWritableDatabase` (SQLCipher's Room helper), which is **only used when `SupportOpenHelperFactory` is active**. If the passphrase were lost, `verifyEncryptedDb()` would have failed, `factory` would be `null`, and the stack would show framework `android.database.sqlite.*` classes instead.
- Therefore the passphrase was correct — verification passed — but Room's actual open still failed.

**The real bug:** `verifyEncryptedDb()` opened with `OPEN_READONLY`, but Room calls `getWritableDatabase` (`OPEN_READWRITE`). In SQLCipher with WAL mode (Room's default), read-only opens skip WAL recovery, while read-write opens must replay the WAL. If the WAL file was corrupted (from process kill mid-write by the hourly `PollenCheckWorker`), read-only passed but read-write failed with `SQLITE_NOTADB`.

**Why "multiple times per day":** It was a crash loop. Corrupted WAL persists across restarts → verify(READONLY) always passes → Room(READWRITE) always fails → crash → repeat. The 2.4-second process uptime confirmed this.

### Key Insight
**Always match verification conditions to actual usage conditions.** `OPEN_READONLY` vs `OPEN_READWRITE` have different WAL recovery behaviour in SQLCipher. A verification check that doesn't match the production codepath creates a false sense of safety.

### Fix Applied (commit pending, testing in progress)
Four-layer defence:

1. **`DatabaseEncryption.kt`** — Pre-Room defence:
   - `verifyEncryptedDb()` changed to `OPEN_READWRITE` to match Room
   - On verify failure: delete WAL/SHM + retry (preserves data), then delete DB as last resort
   - `getEncryptedPrefs()` wrapped to return null on Keystore failure
   - `dbWasReset` flag + `markDbReset()` for UI notification

2. **`AppDatabase.kt`** — Room open defence:
   - **`JournalMode.TRUNCATE`** — Eliminates WAL entirely. App is single-process with low write frequency; WAL's concurrent read/write benefit is unnecessary. No WAL = no WAL corruption. **This is the primary preventive fix.**
   - **`openWithRecovery()`** — Force-opens DB after build with 3-tier recovery: normal open → WAL cleanup + retry → delete + recreate

3. **`PollenCheckWorker.kt`** — Crash containment:
   - `doWork()` wrapped in try-catch returning `Result.retry()`. Background worker DB failure no longer crashes the app process.

4. **`MainActivity.kt`** — User notification:
   - One-shot `AlertDialog` when `dbWasReset` is true, explaining cached data was cleared but profiles/settings (stored in EncryptedSharedPreferences, not Room) are unaffected.

### Files Changed
- `app/src/main/java/com/ryan/pollenwitan/data/security/DatabaseEncryption.kt`
- `app/src/main/java/com/ryan/pollenwitan/data/local/AppDatabase.kt`
- `app/src/main/java/com/ryan/pollenwitan/worker/PollenCheckWorker.kt`
- `app/src/main/java/com/ryan/pollenwitan/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pl/strings.xml`

### If the Issue Persists
If crashes continue after this fix, investigate:
- Whether `JournalMode.TRUNCATE` is actually being applied (check `PRAGMA journal_mode` at runtime)
- Whether the crash is now happening at a different point (the `openWithRecovery` should catch it, but check logs)
- Whether there's a second code path that opens the DB outside of `AppDatabase.getInstance()` (e.g. data export/import)
- Whether Android 16 secondary user profiles have Keystore quirks causing `EncryptedSharedPreferences` to silently re-key
- Adding `PRAGMA cipher_integrity_check` after open as a diagnostic

### Pattern to Avoid
Previous fix (commit a7648f0) tried to "fall back to unencrypted" by setting `factory = null` when verification failed. This was impossible — you can't read an encrypted DB without the passphrase. The result was either a framework SQLite crash (different error) or a cycle where `isAlreadyEncrypted()` re-flagged the DB as encrypted each launch. **Don't create fallback paths that are logically impossible.**

---

## 2026-04-04 — Passphrase Loss Despite WAL Fix

### The Problem
After deploying the WAL corruption fix (TRUNCATE mode, OPEN_READWRITE verify, 3-tier recovery), the DB was still lost overnight. The recovery code worked (no crash — the `dbWasReset` dialog appeared), but it hit the last-resort "delete DB" path.

### Key Evidence
- Logcat showed the process killed/restarted **5 times** between 04:19–04:23 (PIDs 30306 → 30849 → 32471 → 7122 → 8283) — typical WorkManager + Android battery optimization behaviour
- No app-level logs captured (only system `events`/`audit` buffers visible in the report), so we couldn't see which recovery tier triggered the reset
- `JournalMode.TRUNCATE` was active, ruling out WAL corruption as the cause
- The device is Android 16 (developer preview), secondary user profile (`full.secondary`)

### Root Cause (Initially Hypothesised — Later Disproven)
Initial hypothesis was EncryptedSharedPreferences losing the passphrase due to Keystore re-keying. A passphrase backup mechanism was added to test this. **This hypothesis was WRONG** — see the 2026-04-04 follow-up below.

### Fix Applied
**Passphrase backup mechanism** — store the passphrase in both EncryptedSharedPreferences AND plain SharedPreferences:

1. **Plain SharedPreferences backup** (`pollenwitan_db_key_backup`) stores the passphrase in Base64 and a SHA-256 fingerprint. This survives Keystore invalidation.
2. **Fingerprint mismatch detection** — on each init, compare ESP passphrase fingerprint against the backup. If they differ, the Keystore was re-keyed; use the backup passphrase instead.
3. **ESP-empty recovery** — if ESP returns no passphrase but backup exists, restore from backup.
4. **Verify fallback** — if ESP passphrase fails DB verification, try backup passphrase before deleting.
5. **All writes use `.commit()`** (synchronous) instead of `.apply()` (async).
6. **Comprehensive diagnostic logging** — every decision point logs the passphrase fingerprint and file sizes, so the next failure will be fully diagnosable.

### Security Trade-off
Storing the passphrase in plain SharedPreferences reduces the encryption benefit — it's readable by root or via ADB backup. However:
- `android:allowBackup="false"` prevents ADB backup extraction
- The alternative (losing user health data repeatedly) is worse
- Profiles and settings remain in EncryptedSharedPreferences (higher-value targets)
- The DB primarily contains cached forecasts, dose history, and symptom diary entries

### Files Changed
- `app/src/main/java/com/ryan/pollenwitan/data/security/DatabaseEncryption.kt` — major rewrite of passphrase management
- `app/src/main/java/com/ryan/pollenwitan/data/local/AppDatabase.kt` — enhanced recovery logging

### Outcome
The passphrase backup was NOT needed — diagnostic logging in the next run proved the passphrase was stable. See below.

---

## 2026-04-04 (follow-up) — On-Disk File Corruption, Not Key Loss

### The Problem
Despite all encryption-layer hardening (TRUNCATE mode, READWRITE verify, passphrase backup), the DB was still lost within ~1 hour of a fresh install + JSON import.

### Diagnostic Evidence (from the new logging)
```
DatabaseEncryption: Passphrase loaded from ESP [fp=17abf6d1]
DatabaseEncryption: DB verify: file=45056b, wal=none, shm=none, fp=17abf6d1
SQLiteConnection: Database keying operation returned:0
sqlcipher: ERROR CORE sqlcipher_page_cipher: hmac check failed for pgno=1
sqlcipher: ERROR CORE sqlite3Codec: error decrypting page 1 data: 1
```

And 20 minutes earlier, the system itself flagged it:
```
sqlite_db_corrupt: Database file corrupt=/data/user/12/com.ryan.pollenwitan/databases/pollenwitan.db
```

### Root Cause (Confirmed)
**Physical on-disk corruption of the database file.** The passphrase is correct and stable (`fp=17abf6d1` throughout, loaded from ESP, `keying operation returned:0`). But the actual bytes on page 1 of the DB file no longer match their HMAC. The file itself has been corrupted at the storage layer.

Key evidence chain:
1. Passphrase fingerprint is consistent across runs — **not** a key loss issue
2. No WAL or SHM files present — **not** a WAL corruption issue
3. `keying operation returned:0` — passphrase was accepted by SQLCipher (key setup succeeded)
4. `hmac check failed for pgno=1` — page 1 data on disk doesn't match its HMAC
5. `sqlite_db_corrupt` event from the system 20 minutes before our code even ran
6. File is only 45056 bytes (~11 pages at 4096 page size) — even page 1 is corrupt

### Why SQLCipher Amplifies the Damage
With SQLCipher encryption, every page has an HMAC. A single flipped bit on ANY page causes the HMAC check to fail, making the entire page unreadable. Since page 1 contains the SQLite header and schema, corruption there makes the entire DB unreadable.

Without encryption, the same bit-flip would:
- Corrupt at most one row or index entry
- Leave the rest of the DB readable
- Be recoverable with `PRAGMA integrity_check` + manual repair

### Environment Details
- Device: Pixel 9 (`tegu`), Android 16 developer preview (`BP4A.260205.001`)
- User type: `full.secondary` (user ID 12, path `/data/user/12/`)
- Storage corruption happening consistently (every session, within 1-2 hours)
- Multiple process restarts overnight from WorkManager/battery optimization
- The `auditd` SELinux denials (`denied { read }` for various system properties) suggest the secondary user profile has restricted access to certain system properties, but this shouldn't affect file I/O

### Resolution
**Remove SQLCipher encryption from the Room database entirely.** The DB is already protected by:
- Android's per-user file-based encryption (FBE) at the storage layer
- The app's private data directory (inaccessible to other apps without root)
- `android:allowBackup="false"` (can't be extracted via ADB backup)

Sensitive data (profiles, medicine assignments, location settings) remains in EncryptedSharedPreferences. The Room DB contains cached forecasts (replaceable), dose history, and symptom diary entries — health-adjacent data that is adequately protected by Android's native FBE.

### What to Clean Up
- Remove `DatabaseEncryption` class entirely (or reduce to a no-op)
- Remove `net.zetetic:sqlcipher-android` dependency from `build.gradle.kts`
- Remove `openHelperFactory()` from Room builder
- Handle migration from encrypted DB → unencrypted (existing users)
- Remove passphrase backup SharedPreferences (`pollenwitan_db_key_backup`)
- Remove ESP prefs file (`pollenwitan_db_key`)
- Keep the 3-tier recovery in `AppDatabase` as it's still useful for general corruption
- Keep the `dbWasReset` flag and user notification

### Key Lesson
**Don't add encryption layers on top of already-encrypted storage unless there's a specific threat model that requires it.** Android's file-based encryption already protects app data at rest. Adding SQLCipher on top created a fragile system where minor storage-level corruption (which FBE can tolerate) became total data loss (because SQLCipher's HMAC verification is all-or-nothing per page). The additional encryption provided negligible security benefit over FBE while massively increasing fragility.

### Hypotheses Explored and Ruled Out (Summary)
| Hypothesis | Evidence | Verdict |
|---|---|---|
| WAL corruption from process kill | Switched to TRUNCATE mode, no WAL files present | Ruled out |
| OPEN_READONLY vs OPEN_READWRITE verify mismatch | Fixed, but issue persisted | Ruled out as root cause |
| EncryptedSharedPreferences losing passphrase | Fingerprint stable (`17abf6d1`), ESP returned correct key | Ruled out |
| Passphrase not written to disk (.apply() race) | Switched to .commit(), passphrase stable | Ruled out |
| Android Keystore re-keying on secondary user | Backup mechanism not triggered | Ruled out |
| On-disk file corruption | `hmac check failed for pgno=1`, `sqlite_db_corrupt` system event | **Confirmed** |
