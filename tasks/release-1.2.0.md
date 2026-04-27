# Release 1.2.0 Plan

Feature-heavy minor release following the 1.1.1 dependency-bump patch.

**Deferred to a later release:**
- #9 Longer-term data view / export — app is ~1 month old; no user has meaningful long-term data yet.
- #19 Symptom prediction — same reason; the ±20% similarity lookup needs a meaningful diary history to produce useful predictions.

---

## Wave 1 — Foundations & quick wins

Land schema/data-layer changes together so there's a single Room migration step for users, and bank some fast UX wins.

- [ ] **#14 — Dashboard: wind speed/direction & rain probability from Open-Meteo**
  - [ ] Extend `data/remote/dto/AirQualityResponse.kt` with `wind_speed_10m`, `wind_direction_10m`, `precipitation_probability` (or hourly equivalents)
  - [ ] Extend `domain/model/AirQualityData.kt` with the new fields
  - [ ] Map fields through `data/repository/AirQualityRepository.kt`
  - [ ] Add a compact weather-context row below the allergen cards on `ui/screens/DashboardScreen.kt`
  - [ ] Use Material Symbols icons for wind/rain
  - [ ] Graceful null handling (off-season / unavailable)
  - [ ] Confirm no extra API calls — single existing daily fetch

- [ ] **#86 — Free-text note on daily check-in**
  - [ ] Add nullable `notes: String?` column to the diary entity
  - [ ] Room migration (bundle with any other schema work in this wave)
  - [ ] Add multi-line text field to the daily check-in screen
  - [ ] Persist via existing repository
  - [ ] Display the note on diary history items

- [ ] **#108 — Pull-to-refresh gesture on Dashboard**
  - [ ] Wrap dashboard content in `PullToRefreshBox` (Compose Material 3)
  - [ ] Wire to the existing refresh action in `DashboardViewModel`
  - [ ] Move the refresh button to the top of the screen, smaller (or remove if redundant)
  - [ ] Verify pull state survives configuration change

- [ ] **#87 — Dosage times: full 00:00–23:00 range + validation**
  - [ ] Expand the hour chips from 06:00–22:00 to 00:00–23:00
  - [ ] Add validation: cannot select more hours than the configured doses-per-day
  - [ ] Disable / grey-out additional chips once the limit is reached
  - [ ] Confirm save path rejects invalid combinations

---

## Wave 2 — Single-screen features

Independent feature work, no shared dependencies with later waves.

- [ ] **#13 — Pollen Calendar screen**
  - [ ] Flesh out the existing `PollenCalendar` route (already in `Screen.kt`)
  - [ ] Hardcoded climatology table for the 6 pollen types (curated reference values)
  - [ ] Horizontal bar chart, one row per pollen type, Jan–Dec on x-axis
  - [ ] Intensity gradient shading (low/moderate/high) per month
  - [ ] Vertical "you are here" marker for the current month
  - [ ] Tap a bar to surface a short pollen-type description
  - [ ] Verify dark + light theme rendering
  - [ ] Confirm side-drawer entry is wired up

- [ ] **#17 — Smart notification timing**
  - [ ] Add lead-time preference to `NotificationPrefsRepository` (default 90 min, range 30–180)
  - [ ] In `worker/PollenCheckWorker.kt`, after each fetch find the highest-threshold allergen's peak hour
  - [ ] Compute trigger time = peak − lead-time
  - [ ] Fall back to static morning-briefing time if computed time would be earlier
  - [ ] Persist next-trigger time so it survives app restart
  - [ ] Handle peak-on-following-calendar-day correctly
  - [ ] Off-season fallback (flat/null pollen → static time)
  - [ ] Expose lead-time slider/dropdown in `SettingsScreen.kt`

---

## Wave 3 — Analytics layer

Build shared aggregation helpers once, reuse across the digest and effectiveness features. Introduce a small `domain/analytics/` package rather than scattering logic across ViewModels.

- [ ] **#15 — Weekly digest summary card**
  - [ ] Create `domain/analytics/` package
  - [ ] Aggregation helper: last-7-days average symptom severity, medication adherence %, worst day, best day, dominant allergen
  - [ ] Per-profile, switches with active profile
  - [ ] Handles partial weeks gracefully
  - [ ] Empty state: "No data yet" when fewer than 1 day of diary entries
  - [ ] Card placement on Dashboard (collapsed by default, expandable) — confirm with screen mock first
  - [ ] Tap navigates to full Trends / history view

- [ ] **#21 — Medication effectiveness tracking**
  - [ ] Reuse aggregation layer from #15
  - [ ] Per-medication breakdown: avg symptom score on days taken vs days not taken, % difference, n in each group
  - [ ] Minimum sample size guard (≥5 days each side)
  - [ ] "Not enough data" empty state below the threshold
  - [ ] Calculation logic in `domain/analytics/`, NOT in ViewModel
  - [ ] Render as "Medication effectiveness" card on Trends screen
  - [ ] Results refresh after each new dose/diary entry

---

## Wave 4 — Surfacing insights

Lands last because it depends on the discovery engine producing stable findings.

- [ ] **#18 — Correlation insight chip on Dashboard**
  - [ ] Confirm the discovery engine exposes a query API for high-confidence findings (pre-flight check before starting)
  - [ ] Define confidence threshold for surfacing
  - [ ] Render dismissible chip below allergen cards on Dashboard
  - [ ] Chip text: "💡 Your sneezing correlates strongly with Birch pollen — tap to learn more"
  - [ ] Tap navigates to discovery detail screen
  - [ ] Per-insight dismissal persisted to DataStore (new repository or extension of existing prefs repo)
  - [ ] Per-active-profile only
  - [ ] Show at most one chip at a time; rotate on next app open if multiple findings exist
  - [ ] No chip rendered when no actionable findings exist

---

## Release checklist

- [ ] All wave items merged into `release/1.2.0`
- [ ] Update `tasks/lessons.md` with anything learned during the release
- [ ] Bump `versionName` to `1.2.0` and increment `versionCode` in `app/build.gradle.kts`
- [ ] Draft `tasks/release-notes-1.2.0.md`
- [ ] Annotated tag `v1.2.0`
- [ ] Merge `release/1.2.0` → `master` (`--no-ff`)
- [ ] GitHub Release with signed APK attached
- [ ] Close milestone `rel/1.2.0`; move #9 and #19 to the next milestone
