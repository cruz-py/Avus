# Avus

Android app that helps a family apply for a place for a relative in residential care homes for the elderly in Portugal (ERPI, *Estruturas Residenciais para Pessoas Idosas*, commonly "lares").

The app's UI and the letters it writes are in European Portuguese (pt-PT); the code is in English.

## Features

- **Lares (care homes)** — the official Carta Social list of ERPI ships with the app (other lists can be imported as CSV, e.g. Cascais's open data); shown as a list or a map (MapLibre + OpenFreeMap), with occupancy ("N vagas"), distance sorting ("Perto de…"), search, district/municipality filters, multi-select, and homes added or edited by hand.
- **Utente (resident)** — profile of the resident ("Ficha"), of the relative who contacts the homes ("Contacto"), checklist of the documents homes usually ask for, with attachments (PDF/photo).
- **Enviar (send)** — generates the pre-registration letter (editable) and opens the email app with everything filled in: one email to all homes (in Bcc) or one per home; optional PDF of the letter.
- **Respostas (replies)** — follow-up of each contact (sent, replied, visit, waiting list, place available…) with dated notes; replies can be shared into the app from any email/message app, the app asks how a call went, and a daily on-device reminder flags homes without an answer for a week.

## Public page

User-facing information lives in the public repository **[cruz-py/Avus-app](https://github.com/cruz-py/Avus-app)**: what the app does, APK releases, the privacy policy (`PRIVACIDADE.md`, linked from the app's Definições) and bug reports (Issues). This repository (`Avus`, the code) stays private.

## Privacy

All data stays on the phone (backups disabled). Nothing is sent until the user taps "Send" in their own email app.
The map loads OpenStreetMap tiles from OpenFreeMap (only the map area being viewed); distance sorting
is computed on the phone from postal codes, without sending the user's address anywhere.

## Development

- Kotlin + Jetpack Compose (Material 3), minSdk 26
- `./gradlew assembleDebug` — build
- `./gradlew bundleRelease` — App Bundle for Google Play (each phone downloads only its CPU's native code)
- `./gradlew assembleRelease` — per-CPU APKs (`app-arm64-v8a-release.apk` for almost all phones) plus a universal APK
- `./gradlew testDebugUnitTest` — unit tests (CSV importer, letter generator, storage migration)
- `tools/cartasocial/` — script that builds the national ERPI CSV from cartasocial.pt. The app bundles
  that CSV from `app/src/main/assets/`, but it is not kept in git: generate it before a release
  (see `tools/cartasocial/README.md`)
- `branding/` — logo sources (`avus-icon.svg`, `avus-mark.svg`) and the 512 px store icon; the
  app's colour schemes are generated from the icon's navy (#1F4E8C)

## Release

1. Refresh the ERPI list if it is old (`tools/cartasocial/README.md`).
2. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
3. `./gradlew clean assembleRelease`. Signing reads `AVUS_RELEASE_STORE_FILE`,
   `AVUS_RELEASE_STORE_PASSWORD` and `AVUS_RELEASE_KEY_ALIAS` from `~/.gradle/gradle.properties`
   (without them the APKs are unsigned). Every update must use the same key.
4. Try the release build (R8) on a device or emulator before publishing.
5. Tag `vX.Y` here, and publish a release on [cruz-py/Avus-app](https://github.com/cruz-py/Avus-app/releases)
   with `Avus-X.Y-arm64-v8a.apk`, `Avus-X.Y-armeabi-v7a.apk`, `Avus-X.Y-universal.apk` and their
   `SHA256SUMS.txt`; release notes in pt-PT.

Code layout (`app/src/main/java/pt/avus/app/`):

- `data/` — models, JSON storage (`Repository`), CSV import (`CsvImporter`), profile fields
- `letter/` — letter text (`LetterGenerator`), PDF and email/phone/map intents (`Sender`)
- `ui/` — Compose screens and the `AvusViewModel`
