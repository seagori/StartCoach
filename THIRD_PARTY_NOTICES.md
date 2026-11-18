# Third-party notices

One Step (한걸음) — Copyright (C) 2026 seagori (https://github.com/seagori)

One Step includes the following third-party components. This list covers
direct dependencies declared in `gradle/libs.versions.toml`. Transitive
dependencies are distributed under the licenses of their respective upstream
projects.

Full license texts are provided in the `licenses/` directory and are also
available in the app under **Settings → License information**.

## Android application

| Component | Version | License | Copyright |
|---|---|---|---|
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | The Android Open Source Project |
| AndroidX Lifecycle (runtime-ktx, runtime-compose, viewmodel-compose, process) | 2.8.7 | Apache-2.0 | The Android Open Source Project |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 | The Android Open Source Project |
| AndroidX Navigation Compose | 2.8.8 | Apache-2.0 | The Android Open Source Project |
| AndroidX Compose (BOM, ui, ui-graphics, material3, material-icons-extended, ui-tooling) | 2025.02.00 | Apache-2.0 | The Android Open Source Project |
| AndroidX Room (runtime, ktx, compiler) | 2.6.1 | Apache-2.0 | The Android Open Source Project |
| AndroidX Security Crypto | 1.1.0 | Apache-2.0 | The Android Open Source Project |
| AndroidX WorkManager | 2.10.0 | Apache-2.0 | The Android Open Source Project |
| AndroidX CameraX (core, camera2, lifecycle, view) | 1.3.4 | Apache-2.0 | The Android Open Source Project |
| AndroidX Glance AppWidget | 1.1.1 | Apache-2.0 | The Android Open Source Project |
| Kotlin standard library and Kotlin Gradle plugins | 2.1.10 | Apache-2.0 | JetBrains s.r.o. |
| Kotlin Symbol Processing (KSP) | 2.1.10-1.0.31 | Apache-2.0 | Google LLC |
| Kotlin Coroutines | 1.10.1 | Apache-2.0 | JetBrains s.r.o. |
| kotlinx.serialization | 1.8.0 | Apache-2.0 | JetBrains s.r.o. |
| OkHttp (and Okio) | 4.12.1 | Apache-2.0 | Square, Inc. |
| ZXing Core | 3.5.3 | Apache-2.0 | ZXing Authors |

## Desktop application

| Component | Version | License | Copyright |
|---|---|---|---|
| Compose Multiplatform (runtime, foundation, ui, material3, icons) | 1.7.3 | Apache-2.0 | JetBrains s.r.o. |
| Kotlin Coroutines Swing | 1.10.1 | Apache-2.0 | JetBrains s.r.o. |
| kotlinx.serialization | 1.8.0 | Apache-2.0 | JetBrains s.r.o. |
| OkHttp (and Okio) | 4.12.1 | Apache-2.0 | Square, Inc. |
| Bouncy Castle (bcpkix, bcprov, bcutil) | 1.78.1 | Bouncy Castle Licence | The Legion of the Bouncy Castle Inc. |
| JmDNS | 3.5.9 | Apache-2.0 | JmDNS contributors |
| ZXing Core | 3.5.3 | Apache-2.0 | ZXing Authors |

## Build tooling (not distributed with the apps)

| Component | Version | License | Copyright |
|---|---|---|---|
| Gradle | 8.13 | Apache-2.0 | Gradle Inc. |
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | Google LLC |
| Compose Multiplatform Gradle plugin | 1.7.3 | Apache-2.0 | JetBrains s.r.o. |

## Test-only dependencies (not distributed)

| Component | Version | License | Copyright |
|---|---|---|---|
| JUnit Jupiter | 5.11.0 | Eclipse Public License 2.0 | JUnit Team |

## Notes

- The application itself is licensed under the GNU General Public License,
  version 3 or later. See `LICENSE` and `NOTICE`.
- Branding assets are licensed under CC BY-SA 4.0. See `branding/LICENSE`.
- **ML Kit is not included.** The QR pairing scanner uses ZXing Core
  (Apache-2.0), so no proprietary ML Kit or Google Play services components
  are bundled.
- AndroidX artifacts are covered by the Apache License, Version 2.0. The
  Android Open Source Project NOTICE information applies.
- Apache-2.0 requires retaining copyright and NOTICE information. The full
  Apache License text is included in `licenses/Apache-2.0.txt`.
- The Bouncy Castle Licence is an MIT-style license. Its text is included in
  `licenses/Bouncy-Castle.txt`.
