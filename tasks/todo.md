# PollenWitan Enhancement To-Do List

## Completed

- [x] **Profile Management Screen** — Add/edit/delete profiles with allergen picker and threshold configuration
- [x] **Expand Allergen Set** — Add Mugwort, Ragweed, Olive pollen types (Open-Meteo supports all 6)
- [x] **Remove Hardcoded Defaults** — Replace Ryan/Olga auto-seeding with empty-state onboarding prompt
- [x] **App Icon** — Thurisaz rune adaptive icon matching NewTidweard pattern
- [x] **Theme Toggle in Drawer** — Manual dark/light toggle, persisted in DataStore
- [x] **Per-Profile Locations** — Allow different household members to track pollen for different areas
- [x] **Medication Tracker** — Medicine definitions in Settings, per-profile assignments with dosage/schedule, dashboard dose confirmation, reminder notifications
- [x] **First-Run Onboarding Flow** — Guided wizard for creating first profile (name, allergens, asthma, location)
- [x] **Forecast & Dashboard UX** — Severity legend, abbreviation key, units (grains/m³), period time ranges, AQI severity labels, "Peak" label
- [x] **Polish Localisation** — Full English and Polish string resources, runtime language switching via AppCompat
- [x] **Cross-Reactivity Warnings** — Info screen with pollen-to-pollen and OAS food data, contextual hints in allergen selection
- [x] **Home Screen Widget** — Jetpack Glance widget showing selected profile's pollen levels, AQI, severity colours

## Planned

- [x] **Pre-Season Medication Alerts** — Advance warning to start taking medication (e.g., 1 month before typical season for user's selected allergies)
- [x] **Medication History** — Persist daily dose confirmations to Room so medication adherence is available for historical review
- [x] **Symptom Diary** — Configurable symptoms per profile (defaults + custom via Settings), daily evening check-in (in-app + notification) with 0–5 severity rating per symptom, auto-logs daily peak pollen/AQI values alongside entries
- [x] **Long-Term Symptom View** — Graphical timeline screen combining symptom severity trends, daily peak pollen levels, AQI, and medication adherence over configurable date ranges (7d / 30d / 90d / season). Line charts for symptom averages and pollen counts, bar overlays for dose adherence. Per-profile, scrollable, with tap-to-inspect day detail. Helps users identify which pollens trigger their symptoms by correlating visual trends. Replaces the History Screen / Season Comparison concept.
- [x] **Data Export/Import** — Export/import profiles, medication history, symptom diary, and settings as JSON. Room database encrypted with SQLCipher. CSV symptom export for medical practitioners
- [x] **Widget Enhancements** — Add daily peak pollen values to the widget display, include a manual refresh button in the widget corner (Glance `ActionCallback`), and review layout for additional useful fields (AQI, medication status)
- [x] **Notification Enhancements** — Combine daily notifications for all profiles into a single grouped notification instead of one per profile. Add PendingIntents so tapping a notification navigates to the relevant screen (e.g., morning briefing → dashboard, medication reminder → dashboard, symptom reminder → check-in screen)
- [x] **Symptom Diary Back-Fill** — Allow users to log symptom entries for past dates from the diary browser screen, not just the current day. Date picker to select the target date, pre-fill if an entry already exists for that date
- [x] **Evening Check-In UX** — Make it clearer on the dashboard card that the daily symptom check-in is intended for the evening (e.g., subtitle text, time-aware messaging like "Log your evening check-in" vs "Check back this evening")

## Unit Test Coverage (Issue #10)

Incremental test coverage plan. Each task is a self-contained PR targeting `release/1.0.0`.

### Batch 1 — Domain Layer (Pure Logic, No Android Dependencies)

- [ ] **Test: SeverityClassifier** — `pollenSeverity()` boundary conditions for all 6 pollen types (zero, within-band, at-boundary, above-maximum), `aqiSeverity()` for all 5 AQI bands, negative/null-equivalent inputs. JUnit 4, no mocks needed.

- [ ] **Test: ThresholdCalibrationEngine** — Data sufficiency validation (below min entries, below min days, below min clean days), `weightedPercentile()` correctness, suggestion filtering (significance threshold), multi-level threshold ordering, confidence scoring tiers (Low/Medium/High), medication adherence effect on weighting, AQI confounding detection. JUnit 4 + hand-crafted `CalibrationDataPoint` fixtures.

- [ ] **Test: AllergenDiscoveryEngine** — Data sufficiency validation, `pearsonCorrelation()` against known expected values, `adjustedSymptomScore()` weighting (medication present/absent), `CorrelationStrength` classification thresholds, median concentration detection. JUnit 4 + hand-crafted `DiscoveryDataPoint` fixtures.

### Batch 2 — Repository Logic (Requires Fake/In-Memory Implementations)

- [ ] **Test: AirQualityRepository** — Cache key generation (coordinate rounding), cache expiry logic, `parseReadingsAtIndex()` for missing/null hourly values, period summary computation (morning 06–11, afternoon 12–17, evening 18–23), peak pollen detection across a 4-day forecast. JUnit 4 + MockK for `CachedForecastDao` + fake `AirQualityApi`.

- [ ] **Test: DoseTrackingRepository** — `confirmDose()`/`unconfirmDose()` round-trip, date-based key isolation (yesterday's confirmations do not appear today), `getHistoryForDateRange()` boundary inclusivity. JUnit 4 + in-memory SharedPreferences fake + MockK for Room DAO.

### Batch 3 — Background Worker Logic

- [ ] **Test: PollenCheckWorker (unit-level)** — Location grouping deduplication, `buildMorningSummary()` text for single/multi-profile groups, `findThresholdBreaches()` against SeverityLevel thresholds, compound risk detection (asthma flag + PM2.5/PM10 threshold), medication reminder hour matching, pre-season deduplication (same allergen not re-alerted in same year), GPS refresh interval guard. JUnit 4 + MockK; no `WorkManager` instrumented runner needed at this level.

### Batch 4 — ViewModel Logic (Requires `kotlinx-coroutines-test`)

- [ ] **Test: DashboardViewModel** — Medicine slot construction from profile assignments and today's confirmations (confirmed/unconfirmed/time-locked states), `allDosesConfirmed` derived state, location resolution (profile-override vs global fallback). JUnit 4 + `TestScope`/`UnconfinedTestDispatcher` + MockK.

- [ ] **Test: SymptomTrendsViewModel** — `DaySnapshot` aggregation from combined diary entries + dose history, pollen JSON round-trip parsing, date range boundary calculation for 7d/30d/90d, expected dose calculation per medicine per day. JUnit 4 + `TestScope` + MockK.

- [ ] **Test: ProfileEditViewModel** — Default threshold generation per pollen type, custom-threshold detection (diverges from defaults), medicine assignment add/remove/update, validation rejection (empty name, invalid threshold ordering), GPS location integration, save serialises all fields correctly. JUnit 4 + `TestScope` + MockK.

### Batch 5 — Data Export/Import

- [ ] **Test: AppDataExporter / AppDataImporter** — Round-trip: export all data → import → verify domain models match originals. Version validation rejects unknown schema version. Order-dependent restoration (medicines before profiles). Error summary generation on partial failure. JUnit 4 + fake repositories.

### Test Infrastructure (Do First)

- [ ] **Add test dependencies** — Add to `app/build.gradle.kts`: `junit:junit:4.13.2`, `io.mockk:mockk:1.13.x`, `org.jetbrains.kotlinx:kotlinx-coroutines-test` (matching coroutines version), `androidx.arch.core:core-testing` (for `InstantTaskExecutorRule`). Confirm `testOptions { unitTests.isReturnDefaultValues = true }` is set.

## Future

- [x] **DataStore Encryption** — Encrypt sensitive DataStore files (profiles, medicines, dose_tracking, location) using EncryptedSharedPreferences
- [x] **Location-Aware Profiles** — Auto-detect travel and switch forecast location
- [x] **Positive Reinforcement** — Display a random encouraging phrase when the user completes all their medications for the day or logs their daily symptom check-in. Pool of varied phrases (localised EN/PL) to keep it fresh
- [x] **Threshold Auto-Calibration** — Use symptom diary data to suggest personalised threshold adjustments
- [x] **Allergen Discovery Mode** — Functionality for users who don't know their specific allergies to track all pollens and use symptom diary correlation to help identify triggers over time. Clear medical disclaimers that the app is not a diagnostic tool and recommends consulting a specialist
- [x] **Onboarding Wizard Improvements** — Streamlined flow for returning users importing JSON data; restructure wizard to support both "I know my allergies" and "help me figure it out" paths
