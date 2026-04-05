# Known Low-Impact Bugs

Identified during code review (2026-04-05). Not urgent, but worth fixing when touching nearby code.

## 1. Non-atomic StateFlow increment in EncryptedPrefsStore

**File:** `data/security/EncryptedPrefsStore.kt:66`

`version.value++` is a read-modify-write that isn't atomic. If two `SharedPreferences` change listeners fire concurrently, one increment could be lost, causing a reactive Flow emission to be silently dropped.

**Fix:** Replace `version.value++` with `version.update { it + 1 }`.

## 2. Unstable medicine assignment ordering in ProfileRepository

**File:** `data/repository/ProfileRepository.kt:206`

`SharedPreferences.getStringSet()` returns a `Set` with no guaranteed iteration order. The resulting `medicineAssignments` list can reorder between reads, potentially causing unnecessary Compose recompositions or UI flickering.

**Fix:** Add `.sortedBy { it.medicineId }` after the `mapNotNull` that builds the medicine assignments list.

## 3. Retry count naming is misleading in AirQualityApi

**File:** `data/remote/AirQualityApi.kt:64-112`

`maxRetries = 3` suggests 3 retry attempts, but the code does `repeat(3)` (3 attempts) plus a final attempt outside the loop = 4 total network requests. Not a functional bug, but could confuse future maintainers.

**Fix:** Either rename to `maxAttempts = 4` and adjust the loop, or remove the final attempt outside the loop and let the last iteration propagate exceptions directly.

## 4. `threshold.low` not used in severity classification

**File:** `domain/model/SeverityClassifier.kt:8-15`

Any pollen value > 0 is classified as "Low", regardless of `threshold.low`. Per the documented thresholds, Birch "Low" is 1–10 grains/m³, so a reading of 0.5 should be "None" but gets "Low". The `low` field is stored, exported, validated, and displayed — but never checked during classification.

**Fix:** Add `value < threshold.low -> SeverityLevel.None` as the first branch in the `when` block.

## 5. `seasonStartDisplay` not locale-aware for Polish

**File:** `domain/model/PollenSeason.kt:45`

`DateTimeFormatter.ofPattern("MMMM d")` always produces month-then-day order. For Polish locale this gives "kwietnia 5" instead of the correct "5 kwietnia". Used in notification text.

**Fix:** Use `DateTimeFormatter.ofPattern("d MMMM")` conditionally, or use `DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)`.

## 6. Misleading comment in AllergenDiscoveryEngine

**File:** `domain/calibration/AllergenDiscoveryEngine.kt:139-143`

Comment says "Reduce symptom weight" but the code *boosts* the score by 1.3x. Logic is correct (accounting for medication masking true severity); only the comment is wrong.

**Fix:** Change comment to "Boost symptom score when medication adherence is high, since medication may be masking true symptom severity".

## 7. Notification IDs shift when profile order changes

**File:** `worker/PollenCheckWorker.kt:107`

Notification IDs are derived from profile list index (`MORNING_BRIEFING_BASE_ID + index`), but `ProfileRepository.getProfiles()` returns profiles from a `StringSet` with no guaranteed iteration order (same root cause as bug #2). If profiles reorder between worker runs, old notifications are never cancelled and new ones are sent with different IDs, causing duplicate notifications.

Affects: morning briefings, threshold alerts, compound risk alerts, medication reminders, symptom reminders, pre-season alerts.

**Fix:** Sort profiles by `id` before `forEachIndexed`, or derive notification IDs from a stable profile ID hash instead of list index.

## 8. Custom profile location silently dropped with blank display name

**File:** `ui/screens/ProfileEditLogic.kt:79-86` and `:35-66`

`validate()` checks lat/lon validity when `useCustomLocation` is true but never checks if `locationDisplayName` is blank. `resolveLocation()` silently returns `null` for blank names. The profile saves without the location override and the user sees "saved successfully" while their custom location is discarded.

Only affects manual coordinate entry (GPS fix auto-fills the name).

**Fix:** Add a `LocationNameBlank` validation reason, or have `resolveLocation` fall back to coordinates as the display name.

## 9. `requestCode()` collision with >= 10 slots

**File:** `worker/MissedDoseAlarmReceiver.kt:86-87`

`MISSED_DOSE_NOTIF_BASE_ID + profileIndex * 100 + assignmentIndex * 10 + slotIndex` overflows when `slotIndex >= 10` — e.g. `(0, 1, 0) = 7010` and `(0, 0, 10) = 7010`. Unlikely today (realistically <= 3 slots) but a latent collision.

**Fix:** Use a hash-based approach: `Objects.hash(profileIndex, assignmentIndex, slotIndex).and(0xFFFF) + MISSED_DOSE_NOTIF_BASE_ID`.

## 10. `isReduceMotionEnabled()` is not reactive

**File:** `ui/screens/DashboardScreen.kt:572-574`

The value is cached once in `remember {}` and never re-read. If the user toggles "remove animations" from the accessibility quick tile while the app is foregrounded, animations won't respond until the composable leaves and re-enters composition.

**Fix:** Observe `ANIMATOR_DURATION_SCALE` reactively via a `ContentObserver` inside a `DisposableEffect`.

## 11. `StaleDataBanner` missing accessibility role

**File:** `ui/components/StaleDataBanner.kt`

The banner is clickable but has no `semantics { role = Role.Button }`. Screen-reader users won't know it's tappable.

**Fix:** Add `Modifier.semantics { role = Role.Button }` to the `Surface`.

## 12. Import error detection via fragile string matching

**File:** `ui/screens/SettingsScreen.kt:134`

`e.message?.contains("encrypted") == true` detects encrypted imports by matching the exception message string. A locale-specific or library-version message change would silently break the "prompt for password" flow.

**Fix:** Throw a custom `EncryptedImportException` from the importer and catch it specifically.
