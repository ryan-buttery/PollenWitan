# PollenWitan Enhancement To-Do List

## Done

- [x] **Profile Management Screen** — Add/edit/delete profiles with allergen picker and threshold configuration
- [x] **Expand Allergen Set** — Added Mugwort, Ragweed, Olive pollen types (all 6 Open-Meteo types)
- [x] **Remove Hardcoded Defaults** — Replaced Ryan/Olga auto-seeding with empty-state onboarding prompt
- [x] **App Icon** — Thurisaz rune adaptive icon with forest theme

## Planned

- [ ] **Theme Toggle in Drawer** — Manual dark/light toggle (like NewTidweard), persisted in DataStore
- [ ] **Per-Profile Locations** — Allow different household members to track pollen for different areas (e.g., home vs workplace)
- [ ] **First-Run Onboarding Flow** — Guided wizard for creating first profile (name, allergens, asthma, location)
- [ ] **ForecastScreen Column Tuning** — Adjust layout for 6 allergen types on narrow screens (may need horizontal scroll or show only tracked allergens)

## Future / Low Priority

- [ ] **Symptom Diary** — Daily symptom logging with threshold auto-calibration over time
- [ ] **Medication Tracker** — Log daily medication intake with history view
- [ ] **Pre-Season Medication Alerts** — Advance warning (~1 month before typical season start) to begin preventative medication for the user's tracked allergens
- [ ] **Cross-Reactivity Warnings** — Birch → Bet v 1 foods (apples, hazelnuts, etc.)
- [ ] **Wear OS Companion Tile** — Quick glance at today's pollen levels
- [ ] **Location-Aware Profiles** — Auto-detect travel and switch forecast location
- [ ] **Season Comparison** — Historical data using CAMS reanalysis
- [ ] **Home Screen Widget** — Jetpack Glance widget with per-profile instances (Phase 2 from design doc)
- [ ] **Polish Localisation** — Full English and Polish string resources
