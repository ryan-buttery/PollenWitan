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

## Future

- [ ] **Pre-Season Medication Alerts** — Advance warning to start taking medication (e.g., 1 month before typical season for user's selected allergies)
- [ ] **Symptom Diary** — Daily symptom logging with threshold auto-calibration over time
- [ ] **Location-Aware Profiles** — Auto-detect travel and switch forecast location
- [ ] **Season Comparison** — Historical data using CAMS reanalysis
- [ ] **Data Export/Import** — Export/import profiles, medication history, and symptom data (after Symptom Diary is implemented)
