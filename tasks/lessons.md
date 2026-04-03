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
