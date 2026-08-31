# FuelApp

Android app for tracking fuel consumption across one or more vehicles. Log
every refuel, get real L/100km numbers, running costs and a range estimate —
offline, on device, no account.

## Features

- **Multiple vehicles** — keep a separate log per car, switch from the top bar.
  Statistics, charts and the widget all follow the selected vehicle.
- **Configurable currency** — set any code or symbol in Settings; it defaults to
  the device locale's currency.
- **Fuel log** — date, odometer, liters, price per liter; total cost is
  calculated as you type, and any two of the three amount fields fill in the third.
- **Partial refuels** — entries can be marked as not-a-full-tank. Consumption is
  measured tank-to-tank between full fills, so partials are accounted for without
  skewing the average.
- **Statistics** — average, best and worst consumption, cost per km, average
  distance between refuels, and totals. Give a vehicle its tank size and you also
  get a predicted range.
- **Charts** — price-per-liter trend and efficiency trend over time, drawn natively.
- **Odometer scanning** — point the camera at the odometer and the reading is
  recognised on device via ML Kit; no image leaves the phone.
- **CSV import / export** — share every vehicle's log out as CSV and read it back
  in; rows are matched to vehicles by name, and older exports still import.
- **Home screen widget** — current consumption at a glance, tap to add an entry.
- **Localised** — English and Hungarian.

## Install

Grab the APK from the [latest release](https://github.com/Bokor-Attila/FuelApp/releases/latest)
and open it on your phone, allowing installation from unknown sources when prompted.
For update notifications, point [Obtainium](https://github.com/ImranR98/Obtainium) at this
repository.

## Requirements

| | |
|---|---|
| JDK | 21 |
| Android Studio | any version supporting AGP 9.2 |
| `minSdk` | 28 (Android 9) |
| `targetSdk` / `compileSdk` | 36 |

## Building

```bash
git clone https://github.com/Bokor-Attila/FuelApp.git
cd FuelApp
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android Studio users can just open the project folder — the SDK path is written
to `local.properties` on first sync and is not tracked.

## Tests and checks

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
./gradlew assembleDebug       # build
```

Consumption math and CSV handling live in plain top-level functions
(`calculateStats`, `calculateConsumption`, `buildCsv`, `parseCsv`) so they are
covered by JVM unit tests without an emulator. `FuelStatsTest` is the place to add
cases when the tank-to-tank rules change.

Database migrations and DAO behaviour are covered by instrumented tests, which need
a connected device or a running emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Room schemas are exported to `app/schemas/` and committed, so schema changes show up
in review.

## Continuous integration

`.github/workflows/android.yml` runs unit tests, lint and a debug build on every
push to `main`, every `feature/**` branch and every pull request. Tagging a commit
`v*` builds a signed, R8-minified release APK and publishes it to a GitHub release.

## Architecture

Single-module app, Kotlin and Jetpack Compose throughout.

| Path | Role |
|---|---|
| `MainActivity.kt` | Compose UI, charts, statistics, CSV handling |
| `FuelViewModel.kt` | State, exposes the selected vehicle's entries as a `StateFlow` |
| `data/FuelData.kt` | Room entities, DAOs, database and migrations |
| `data/SettingsRepository.kt` | Currency and selected vehicle, stored in DataStore |
| `FuelWidgetProvider.kt` | Home screen widget |
| `OdometerScanner.kt` | CameraX preview and ML Kit text recognition |

Data is stored locally in a Room database (`fuel_database`). Nothing is
transmitted anywhere.

## Roadmap

- Signed, R8-minified release builds (the debug APK is large and unshrunk)
- Split `MainActivity.kt` into UI, chart and domain layers
- Per-vehicle currency, and imperial units (mpg) alongside L/100km

## License

Copyright (C) 2026 Attila Bokor

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the [GNU General Public License](LICENSE) for more
details.
