# PollenWitan

[![Latest Release](https://img.shields.io/github/v/release/ryan-buttery/PollenWitan)](https://github.com/ryan-buttery/PollenWitan/releases/latest)

**Pollen & Air Quality Forecast** -- a privacy-respecting Android app that delivers personalised pollen and air quality forecasts tailored to the specific allergenic triggers of each household member.

> **Europe only**: PollenWitan uses the [CAMS European air quality model](https://atmosphere.copernicus.eu/) via the Open-Meteo API (~11 km resolution). Pollen and air quality data is only available for European locations.

---

## Features

- **Per-user allergen profiles** -- track the six European pollen types that matter to you: Birch, Alder, Grass, Mugwort, Ragweed, and Olive. Each profile has its own severity thresholds, asthma flag, and optional location override.
- **Colour-coded dashboard** -- at-a-glance severity indicators for every tracked allergen, European AQI score, and PM2.5/PM10 readings. Switch between household profiles with a single tap.
- **4-day forecast** -- expandable daily cards with morning/afternoon/evening breakdowns, peak severity per allergen, and hourly detail on tap.
- **Symptom diary & trends** -- log daily symptoms, track patterns over time, and correlate them with pollen levels.
- **Allergen discovery mode** -- not sure what you're allergic to? Discovery mode analyses your symptom diary against pollen data to identify likely triggers with confidence ratings.
- **Threshold calibration** -- uses your symptom history to suggest personalised severity thresholds.
- **Cross-reactivity guide** -- educational content on pollen-food cross-reactions (Oral Allergy Syndrome) for each allergen type.
- **Pollen calendar** -- seasonal timeline showing typical pollen seasons with a "you are here" marker.
- **Smart notifications** -- morning briefing (configurable time), threshold breach alerts, compound respiratory risk alerts (pollen + poor air quality for asthma profiles), pre-season medication reminders, and symptom check-in prompts.
- **Medicine tracking** -- assign medicines (tablets, eyedrops, nasal spray) to profiles with dose timing and completion tracking on the dashboard.
- **Home screen widget** -- current pollen readings, peak daily levels, AQI, and medication status at a glance.
- **Data backup & restore** -- export and import all profiles and settings as JSON.
- **Dark and light themes** -- toggle from the navigation drawer.
- **English and Polish localisation**.

---

## User Guide

### Getting Started

When you first open PollenWitan, the onboarding wizard will guide you through setup:

1. **Choose your language** -- English or Polish.
2. **Select your path**:
   - **Known allergies** -- if you already know which pollens affect you, create a profile with your allergens and thresholds straight away.
   - **Discovery mode** -- if you're unsure, start with minimal setup and let the app help identify your triggers over time.
   - **Import backup** -- restore from a previous JSON export.
3. **Set your location**:
   - **Default** -- Poznan, Poland.
   - **GPS** -- automatic location, refreshes every 6 hours.
   - **Manual** -- enter latitude and longitude coordinates.

### Creating a Profile

From **Profiles** in the navigation drawer (or during onboarding):

1. Enter a display name (e.g. "Ryan", "Kasia").
2. Toggle the **asthma** indicator if applicable -- this enables compound risk alerts when pollen coincides with poor air quality.
3. Select which allergens to track (Birch, Alder, Grass, Mugwort, Ragweed, Olive).
4. Optionally adjust severity thresholds per allergen -- the defaults are based on standard clinical ranges.
5. Optionally assign medicines with dosing schedules.

You can create multiple profiles for different household members. Each profile gets its own dashboard view, notifications, and widget instance.

### The Dashboard

The main screen shows the current conditions for the selected profile:

- **Profile switcher** -- tap a chip to switch between profiles.
- **Pollen readings** -- each tracked allergen is shown with a colour-coded severity indicator. Untracked allergens appear dimmed for context.
- **Air quality** -- European AQI score with a severity label, plus PM2.5 and PM10 values.
- **Medicine tracker** -- tick off doses as you take them throughout the day.
- **Symptom check-in** -- an evening prompt to log how you felt, building your symptom history for trend analysis and threshold calibration.

Tap the **refresh button** to manually update data.

### Understanding Severity Colours

PollenWitan uses a consistent colour scheme across the app:

| Colour | Level | Meaning |
|--------|-------|---------|
| Grey | None | No pollen detected or out of season |
| Green | Low | Minimal impact expected |
| Amber | Moderate | Noticeable for sensitive individuals |
| Red | High | Significant symptoms likely |
| Purple | Very High | Severe exposure -- take precautions |

### Forecast View

Navigate to **Forecast** from the drawer to see a 4-day outlook:

- Each day is an expandable card showing peak severity dots for your tracked allergens and peak AQI.
- Expand a card to see morning, afternoon, and evening breakdowns.
- Tap a period for hourly detail.
- Toggle the **severity legend** for a colour/abbreviation reference.

### Symptom Diary & Trends

- **Log symptoms** via the dashboard check-in card or the dedicated Symptom Diary screen.
- **Browse entries** by date in the Symptom Diary.
- **Analyse patterns** in the Symptom Trends screen, which charts symptom severity alongside pollen levels over time.

### Discovery Mode & Threshold Calibration

If you started in discovery mode (or want to refine your profile):

- **Allergen Discovery** analyses your logged symptoms against pollen data to surface likely triggers with confidence ratings. A medical disclaimer is included -- this is informational, not diagnostic.
- **Threshold Calibration** uses your symptom patterns to suggest personalised severity thresholds, so your alerts better match your real-world sensitivity.

Both features improve with more data -- log symptoms consistently for the best results.

### Cross-Reactivity Guide

Accessible from the navigation drawer, this screen explains pollen-food cross-reactions (Oral Allergy Syndrome). For example, birch pollen sensitivity may correlate with reactions to apples, pears, cherries, kiwi, and hazelnuts.

### Pollen Calendar

A seasonal timeline showing when each pollen type is typically active in your region, with a "you are here" marker for the current date.

### Notifications

Configure in **Settings**:

| Channel | Description |
|---------|-------------|
| Morning briefing | Daily summary of today's pollen and air quality at your chosen time |
| Threshold alerts | Triggered when a tracked allergen reaches High or Very High |
| Compound risk | Pollen + poor air quality combined alert (asthma profiles only) |
| Medication reminders | Dose timing prompts based on your medicine schedule |
| Symptom reminders | Evening prompt to log your daily symptoms |

Each channel can be enabled or disabled independently.

### Home Screen Widget

Add the PollenWitan widget to your home screen for at-a-glance information:

- Current pollen readings for tracked allergens
- Peak daily pollen levels
- European AQI with colour coding
- Medication status
- Tap to open the full app; refresh button for manual update

### Settings

- **Location** -- switch between GPS, manual coordinates, or the default location.
- **Notifications** -- enable/disable channels and set the morning briefing time.
- **Medicines** -- add, edit, or remove medicines with type and dosing details.
- **Data export/import** -- back up all profiles and settings as JSON, or restore from a backup.
- **Theme** -- switch between dark and light mode.
- **Language** -- English or Polish.

---

## Data Source

Weather and air quality data provided by [Open-Meteo](https://open-meteo.com/). Pollen data sourced from the Copernicus Atmosphere Monitoring Service (CAMS) -- (C) European Centre for Medium-Range Weather Forecasts (ECMWF). Licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

No API key is required. No personal data is sent to any server -- all profile and preference data stays on your device.

---

## Installation

PollenWitan is distributed as a signed APK via [GitHub Releases](https://github.com/ryan-buttery/PollenWitan/releases/latest).

**Requirements**: Android 8.0 (API 26) or higher.

### Verifying the APK

Before installing, it is recommended to verify the APK signature using [APK Signature Verification](https://github.com/nicehash/AppVerifier) or a similar tool. The release signing key fingerprint is:

```
SHA-256 fingerprint: <TO BE FILLED IN AT RELEASE>
```

### Installing

1. Download the latest `.apk` from [Releases](https://github.com/ryan-buttery/PollenWitan/releases/latest).
2. On your device, enable **Install from unknown sources** for your browser or file manager (if not already enabled).
3. Open the downloaded APK and follow the installation prompts.

---

## Privacy

PollenWitan is designed with privacy in mind:

- **No accounts, no sign-up** -- the app works entirely offline after fetching forecast data.
- **No analytics or tracking** -- no data is collected about your usage.
- **All data stays on-device** -- profiles, symptom logs, and preferences are stored locally using Android DataStore and Room.
- **Network requests** are limited to fetching forecast data from the Open-Meteo API. Only your location coordinates are sent; no personal information.
- **GPS access** is optional -- you can use manual coordinates or the default location instead.

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Fetch pollen and air quality data from the Open-Meteo API |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS-based location (optional) |
| `POST_NOTIFICATIONS` | Deliver morning briefings, threshold alerts, and reminders |
