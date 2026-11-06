# Avus

Android app that helps a family apply for a place for a relative in residential care homes for the elderly in Portugal (ERPI, *Estruturas Residenciais para Pessoas Idosas*, commonly "lares").

The app's UI and the letters it writes are in European Portuguese (pt-PT); the code is in English.

## Features

- **Lares (care homes)** — imports the official Carta Social list (CSV produced by `tools/cartasocial`, or municipal open data such as Cascais's) and keeps only ERPI; search and district/municipality filters; multi-select; add or edit homes by hand.
- **Utente (resident)** — profile of the resident and of the responsible relative; checklist of the documents homes usually ask for, with attachments (PDF/photo).
- **Enviar (send)** — generates the pre-registration letter (editable) and opens the email app with everything filled in: one email to all homes (in Bcc) or one per home; optional PDF of the letter.
- **Respostas (replies)** — follow-up of each contact (sent, replied, visit, waiting list, place available…) with notes.

## Privacy

All data stays on the phone (backups disabled). Nothing is sent until the user taps "Send" in their own email app.

## Development

- Kotlin + Jetpack Compose (Material 3), minSdk 26
- `./gradlew assembleDebug` — build
- `./gradlew testDebugUnitTest` — unit tests (CSV importer, letter generator, storage migration)
- `tools/cartasocial/` — script that builds the national ERPI CSV from cartasocial.pt. The app bundles
  that CSV from `app/src/main/assets/`, but it is not kept in git: generate it before a release
  (see `tools/cartasocial/README.md`)

Code layout (`app/src/main/java/pt/avus/app/`):

- `data/` — models, JSON storage (`Repository`), CSV import (`CsvImporter`), profile fields
- `letter/` — letter text (`LetterGenerator`), PDF and email/phone/map intents (`Sender`)
- `ui/` — Compose screens and the `AvusViewModel`
