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

## Planned

- [ ] **Polish Localisation** — Full English and Polish string resources
- [ ] **Cross-Reactivity Warnings** — Birch → Bet v 1 foods (apples, hazelnuts, etc.), static lookup, dashboard card when relevant allergens are elevated
- [ ] **Home Screen Widget** — Jetpack Glance widget with per-profile instances (Phase 2 from design doc)

## Future

- [ ] **Pre-Season Medication Alerts** — Advance warning to start taking medication (e.g., 1 month before typical season for user's selected allergies)
- [ ] **Symptom Diary** — Daily symptom logging with threshold auto-calibration over time
- [ ] **Location-Aware Profiles** — Auto-detect travel and switch forecast location
- [ ] **Season Comparison** — Historical data using CAMS reanalysis
- [ ] **Data Export/Import** — Export/import profiles, medication history, and symptom data (after Symptom Diary is implemented)
