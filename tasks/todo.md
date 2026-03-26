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
- [ ] **Symptom Diary** — Configurable symptoms per profile (defaults + custom via Settings), daily evening check-in (in-app + notification) with 0–5 severity rating per symptom, auto-logs daily peak pollen/AQI values alongside entries
- [ ] **Long-Term Symptom View** — Graphical timeline screen combining symptom severity trends, daily peak pollen levels, AQI, and medication adherence over configurable date ranges (7d / 30d / 90d / season). Line charts for symptom averages and pollen counts, bar overlays for dose adherence. Per-profile, scrollable, with tap-to-inspect day detail. Helps users identify which pollens trigger their symptoms by correlating visual trends. Replaces the History Screen / Season Comparison concept.
- [ ] **Data Export/Import** — Export/import profiles, medication history, symptom diary, and settings as JSON

## Future

- [ ] **Location-Aware Profiles** — Auto-detect travel and switch forecast location
- [ ] **Threshold Auto-Calibration** — Use symptom diary data to suggest personalised threshold adjustments
