# PollenWitan 1.1.1

## Bug fixes

- **Cache miss on day rollover** ([#84](https://github.com/ryan-buttery/PollenWitan/issues/84)) — If the forecast cache was populated in the last hour of the previous day, the 1-hour TTL would still consider it fresh after midnight, but the cached timestamps no longer covered the current hour and the dashboard crashed. `getCurrentConditions` now silently forces a fresh API fetch on a cache miss before surfacing an error. The same path is used by the manual refresh button, so that flow is fixed too.

## Dependency updates

- `org.jetbrains.kotlin.plugin.serialization` 2.2.20 → 2.2.21
- `androidx.activity:activity-compose` 1.9.3 → 1.13.0
- `androidx.appcompat:appcompat` 1.7.0 → 1.7.1
- `androidx.room:{room-runtime,room-ktx,room-compiler}` 2.8.3 → 2.8.4
