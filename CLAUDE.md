# CLAUDE.md -- PollenWitan Android App

Comprehensive guide for AI assistants working in this repository.

---

## Project Overview

**PollenWitan** is a privacy-respecting Android application that delivers personalised pollen and air quality forecasts tailored to the specific allergenic triggers of each household member. Built on the free Open-Meteo Air Quality API (CAMS European model, ~11 km resolution).

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 (Material You dynamic colour)
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 (Android 15)
- **Version**: 0.1.0 (versionCode 1)
- **Package**: `com.ryan.pollenwitan`
- **Build system**: Gradle with Kotlin DSL (`.gradle.kts` files throughout)
- **DI**: Koin
- **Networking**: Ktor Client (CIO engine) + kotlinx.serialization
- **Local storage**: DataStore (preferences/profiles), Room (cached forecast data)
- **Background work**: WorkManager (periodic fetch), AlarmManager (time-critical alerts)

Key features: Per-user allergen profiles with configurable severity thresholds, colour-coded dashboard, push notifications (morning briefing, threshold breach, compound respiratory risk), home screen widget (Jetpack Glance), 4-day forecast view.

The product design document lives at `PollenWitan-Product-Design-Document.docx` in the project root.

---

## Feature Roadmap

Ordered by development phase, per the product design document.

### Phase 1 -- MVP

1. **Per-User Allergen Profiles** -- Profile model with display name, tracked allergens (birch, alder, grass), severity thresholds per allergen, asthma flag. DataStore persistence. Default profiles for Ryan (birch) and Wife (alder).

2. **Profile Switcher & Dashboard** -- Main screen with per-profile summary: colour-coded allergen levels, AQI, natural-language summary, 24-hour hourly chart of primary allergen. Tab/chip row profile switcher.

3. **Open-Meteo Data Layer** -- Ktor client for the Air Quality API, Room caching, repository pattern. Single fetch per day with manual refresh. Exponential backoff on 429s. Off-season detection (null/zero pollen values).

4. **Push Notifications** -- WorkManager scheduled morning briefing (per-profile summary at configurable time). Threshold breach alerts. Compound risk alerts (pollen + PM2.5/PM10 for asthma profiles). Android notification channels: `morning_briefing`, `threshold_alert`, `compound_risk`.

5. **MVP Release** -- v1.0.0 signed APK on GitHub.

### Phase 2

6. **Home Screen Widget (Jetpack Glance)** -- `SizeMode.Responsive` with compact (2x1) and expanded (4x2) layouts. Per-profile widget instances. Refresh tied to WorkManager fetch.

7. **Multi-Day Forecast View** -- 4-day scrollable forecast, morning/afternoon/evening periods, peak levels per allergen, AQI trend. Tappable day cards with hourly breakdown.

8. **Polish Localisation** -- Full English and Polish string resources.

### Future / Low Priority

- Symptom diary with threshold auto-calibration
- Medication reminders on high-pollen days
- Cross-reactivity warnings (birch -> Bet v 1 foods)
- Wear OS companion tile
- Location-aware profiles (travel detection)
- Season comparison using CAMS reanalysis data

---

## Repository Structure

```
PollenWitan/
+-- CLAUDE.md
+-- PollenWitan-Product-Design-Document.docx
+-- build.gradle.kts              # Project-level (plugin versions)
+-- settings.gradle.kts
+-- gradle.properties
+-- keystore.properties           # GITIGNORED -- release signing credentials
+-- gradlew / gradlew.bat
+-- gradle/wrapper/
|   +-- gradle-wrapper.properties
+-- app/
    +-- build.gradle.kts          # App-level (dependencies, build variants)
    +-- proguard-rules.pro
    +-- src/main/
        +-- AndroidManifest.xml
        +-- res/
        |   +-- values/
        |       +-- strings.xml
        |       +-- themes.xml
        +-- java/com/ryan/pollenwitan/
            +-- PollenWitanApp.kt         # Application class (Koin init)
            +-- MainActivity.kt
            +-- di/
            |   +-- AppModule.kt          # Koin module definitions
            +-- data/                     # API client, Room DB, DataStore, repos
            +-- domain/                   # Models, use cases
            +-- ui/
            |   +-- theme/
            |   |   +-- Color.kt          # Severity & AQI colour palettes
            |   |   +-- Theme.kt          # Material You + fallback schemes
            |   |   +-- Type.kt
            |   +-- navigation/
            |   |   +-- Screen.kt         # Sealed class route definitions
            |   |   +-- AppNavGraph.kt    # NavHost + bottom navigation
            |   +-- screens/
            |       +-- DashboardScreen.kt
            |       +-- ForecastScreen.kt
            |       +-- SettingsScreen.kt
            +-- widget/                   # Glance widget definitions (Phase 2)
            +-- worker/                   # WorkManager workers
```

---

## Architecture

Single-module project. If the project grows, extraction into `:core:data`, `:core:domain`, `:feature:*` modules can follow.

### Layers

- **`data/`** -- API client (Open-Meteo via Ktor), Room database, DataStore, repository implementations
- **`domain/`** -- Models (`UserProfile`, `AllergenThreshold`, `ForecastData`, `AlertLevel`), use cases (`EvaluateForecast`, `CheckThresholdBreach`, `GenerateNotificationContent`)
- **`ui/`** -- Compose screens, navigation, theme
- **`widget/`** -- Glance widget definitions, widget state management
- **`worker/`** -- WorkManager workers for scheduled fetch and notification dispatch

### Data Flow

1. WorkManager triggers periodic job (default: once daily at 06:00, configurable)
2. Worker calls Open-Meteo API with user's location + tracked allergen parameters
3. Response parsed, validated, persisted to Room (replacing stale data)
4. For each active profile, `EvaluateForecast` compares forecast vs thresholds
5. Notifications dispatched per profile, per channel
6. Widget state updated via `GlanceAppWidget.updateAll()`
7. UI reads from Room (single source of truth) via Flow

### Theming

The app uses **Material 3 with Material You dynamic colour** (Android 12+). On older devices, a green-toned fallback colour scheme is used.

- Access colours via `MaterialTheme.colorScheme` in composables
- **Severity colours** (`SeverityColors` object in `Color.kt`) are fixed across themes for consistent meaning: Grey (none), Green (low), Amber (moderate), Red (high), Purple (very high)
- **AQI colours** (`AqiColors` object) follow a similar fixed palette
- Dark mode first -- both target users prefer dark mode

### Dependency Injection

Koin is initialised in `PollenWitanApp.onCreate()`. All modules are defined in `di/AppModule.kt`. ViewModels, repositories, API clients, and use cases are registered there.

### Persistence

- **DataStore** for user profiles and app preferences
- **Room** for cached forecast data
- Never access DataStore or Room directly from screens -- always go through a repository

### Navigation

Three top-level destinations with a bottom navigation bar: Dashboard, Forecast, Settings. Defined in `Screen.kt` (sealed class) and wired in `AppNavGraph.kt`.

---

## Build & Development

### Toolchain Requirements

- **JDK 17** (`sourceCompatibility`, `targetCompatibility`, `jvmTarget` all set to 17)
- **Android Studio** (latest stable recommended)
- **AGP 8.7.3** / **Kotlin 2.1.0** / **Compose BOM 2024.12.01** / **Gradle 8.11.1**

### Dependency Versions (app/build.gradle.kts)

| Category | Library | Version |
|---|---|---|
| Compose BOM | `androidx.compose:compose-bom` | 2024.12.01 |
| Activity | `activity-compose` | 1.9.3 |
| Navigation | `navigation-compose` | 2.8.5 |
| Lifecycle | `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose` | 2.8.7 |
| Room | `room-runtime` / `room-ktx` | 2.6.1 |
| DataStore | `datastore-preferences` | 1.1.1 |
| Glance | `glance-appwidget` / `glance-material3` | 1.1.1 |
| WorkManager | `work-runtime-ktx` | 2.9.1 |
| Koin | `koin-android` / `koin-androidx-compose` / `koin-androidx-workmanager` | 3.5.6 |
| Ktor | `ktor-client-core` / `ktor-client-cio` / `ktor-client-content-negotiation` / `ktor-serialization-kotlinx-json` | 2.3.12 |
| Serialization | `kotlinx-serialization-json` | 1.7.3 |

### Build Variants

Both variants are **signed with the same keystore** so they can coexist on the same device.

| Variant | App Name | Application ID | Minify | Shrink |
|---|---|---|---|---|
| `debug` | PollenWitan (Debug) | `com.ryan.pollenwitan.debug` | No | No |
| `release` | PollenWitan | `com.ryan.pollenwitan` | Yes | Yes |

### Release Signing

The `keystore.properties` file in the project root (gitignored) references the shared keystore:

```properties
storeFile=/home/ryanb/keystores/runic-meditation.jks
storePassword=...
keyAlias=mykey
keyPassword=...
```

Debug builds also use this signing config (not the default debug key) so both variants can be installed side-by-side.

### Common Gradle Tasks

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties)
./gradlew assembleRelease

# Run lint
./gradlew lint

# Run unit tests
./gradlew test

# Clean
./gradlew clean
```

---

## Branching Strategy

### Branch Types

| Branch | Pattern | Purpose |
|---|---|---|
| `master` | `master` | Stable, production-only. Every commit on master is a tagged release. Never commit directly. |
| Release | `release/X.Y.Z` | Integration branch for a planned release. All feature/fix branches merge here. |
| Feature | `feature/short-description` | New user-facing functionality. Merged into the release branch. |
| Fix | `fix/short-description` | Bug fixes for an upcoming release. Same rules as feature branches. |
| Hotfix | `hotfix/short-description` | Urgent fixes for a published release. Branched from `master`, merged into both `master` and active release branch. |

### Typical Feature Workflow

```
master
  +-- release/1.0.0  (branched from master)
        +-- feature/allergen-profiles   --> PR --> merge into release/1.0.0
        +-- feature/dashboard-ui        --> PR --> merge into release/1.0.0
        +-- fix/api-timeout             --> PR --> merge into release/1.0.0
                                                |
                                      version bump commit
                                                |
                                     tag v1.0.0 (annotated)
                                                |
                                  merge release/1.0.0 -> master (--no-ff)
                                                |
                               GitHub Release + signed APK attached
```

### Merge Rules

| Direction | Strategy | Notes |
|---|---|---|
| `feature/` or `fix/` -> `release/` | Merge commit (`--no-ff`) | Preserves per-feature history |
| `hotfix/` -> `master` | Merge commit (`--no-ff`) | Never rebase or fast-forward master |
| `hotfix/` -> active `release/` | Merge commit | Cherry-pick acceptable if merge is complex |
| `release/` -> `master` | Merge commit (`--no-ff`) | Delete the release branch after merging |

**No direct commits to `master`** -- it only changes via release or hotfix merge.

### Branch Naming Examples

```
release/1.0.0
feature/allergen-profiles
feature/morning-briefing-notification
fix/api-rate-limit-backoff
hotfix/crash-notification-channel
```

Names: lowercase, hyphen-separated, descriptive but concise (3-5 words).

### Commit Message Convention

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short imperative summary>
```

| Type | When |
|---|---|
| `feat` | New user-facing feature |
| `fix` | Bug fix |
| `refactor` | Code change with no behaviour change |
| `chore` | Build config, dependencies, tooling |
| `docs` | Documentation only |
| `test` | Adding or updating tests |

### Versioning (Semantic Versioning)

`MAJOR.MINOR.PATCH`; `versionCode` increments with every published release.

| Bump | When | Example |
|---|---|---|
| `PATCH` | Bug fixes only (hotfixes) | `1.0.0` -> `1.0.1` |
| `MINOR` | New features, backwards-compatible | `1.0.0` -> `1.1.0` |
| `MAJOR` | Breaking changes or major redesign | `1.0.0` -> `2.0.0` |

### Tagging & Publishing a Release

```bash
# 1. Final commit on release branch: bump versionName + versionCode in app/build.gradle.kts
# 2. Create an annotated tag
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0

# 3. Merge release branch into master (no fast-forward)
git checkout master
git merge --no-ff release/1.0.0
git push origin master

# 4. Delete the release branch
git branch -d release/1.0.0
git push origin --delete release/1.0.0

# 5. Create a GitHub Release from the tag; attach the signed APK
```

---

## Key Conventions

### Kotlin Style

- **Official Kotlin code style** (`kotlin.code.style=official` in `gradle.properties`)
- Use data classes for immutable model types
- Prefer `val` over `var`; use `copy()` for updates
- Sealed classes for discriminated unions (e.g. `Screen`, `AlertLevel`)
- `@Serializable` data classes for API responses

### Compose Conventions

- Access theme colours via `MaterialTheme.colorScheme`
- Use `SeverityColors` and `AqiColors` objects for fixed severity/AQI indicators
- Use `safeDrawingPadding()` at the root layout; inner screens do not need window inset handling
- Screen composables receive dependencies (repository, callbacks) as parameters
- ViewModels injected via `koinViewModel()`

### Navigation Conventions

- Three top-level destinations: Dashboard, Forecast, Settings (bottom nav bar)
- Add new routes to both `Screen.kt` and `AppNavGraph.kt`
- Sub-screens use a back-arrow header with `popBackStack()`

### Persistence Conventions

- **DataStore** for user profiles and preferences
- **Room** for cached forecast data
- Never access DataStore or Room directly from screens -- always go through a repository

### Permissions

- `INTERNET` -- required for Open-Meteo API calls
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` -- will be needed for GPS-based location (can be deferred; app supports manual location pinning as alternative)

---

## Testing

There are currently **no automated tests** in the project. When adding tests:

- Unit tests belong in `app/src/test/`
- Instrumented tests belong in `app/src/androidTest/`
- Prefer JUnit 4 and MockK for mocking
- Compose UI tests use `ComposeTestRule` from `androidx.compose.ui.test.junit4`

---

## Gitignore / Sensitive Files

Files **never to commit**:

| File | Reason |
|---|---|
| `keystore.properties` | Release signing credentials |
| `local.properties` | SDK path (machine-specific) |
| `app/release/` | Built APKs |
| `.gradle/` | Gradle caches |
| `build/` | Build outputs |
| `gradle/wrapper/gradle-wrapper.jar` | Binary, downloaded by wrapper |

---

## API Reference

### Open-Meteo Air Quality API

Base URL: `https://air-quality-api.open-meteo.com/v1/air-quality`

Key parameters:
- `latitude`, `longitude` -- location (default: Poznan 52.4064, 16.9252)
- `hourly` -- `birch_pollen,alder_pollen,grass_pollen,pm2_5,pm10,european_aqi`
- `timezone` -- `Europe/Warsaw`
- `forecast_days` -- `4`

No API key required. Rate limiting applies (respect HTTP 429, exponential backoff).

### Pollen Threshold Defaults (grains/m3)

| Level | Birch | Alder | Grass |
|---|---|---|---|
| Low | 1-10 | 1-10 | 1-5 |
| Moderate | 11-50 | 11-50 | 6-30 |
| High | 51-200 | 51-100 | 31-80 |
| Very High | >200 | >100 | >80 |

---

## AI Workflow Guidelines

### Plan First
- Enter plan mode for any non-trivial task (3+ steps or architectural decisions)
- Write detailed specs upfront to reduce ambiguity
- If something goes sideways, stop and re-plan

### Minimal Impact
- **Simplicity first**: make every change as small as possible
- Only touch what is necessary
- No temporary fixes; find root causes

### Verification Before Done
- Never mark a task complete without proving it works
- **Do not run Gradle builds** -- the development environment does not have the Android SDK. The owner will verify builds manually on their build laptop.
- Instead, verify correctness by reviewing imports, function signatures, call sites, and data flow through careful code reading

### Elegance Check
- For non-trivial changes, ask: "Is there a more elegant solution?"
- If a fix feels hacky, implement the clean solution instead
- Skip over-engineering for simple, obvious fixes

### Autonomous Bug Fixing
- When given a bug report: fix it without hand-holding
- Point at logs, errors, or failing builds -- then resolve them

### Task Tracking
1. Write plan to `tasks/todo.md` with checkable items
2. Track progress; mark items complete as you go
3. Capture lessons in `tasks/lessons.md` after corrections

### Self-Improvement
- After any correction from the user: update `tasks/lessons.md` with the pattern
- Write rules that prevent the same mistake recurring
