# Google Play listing

Store texts and images live in `metadata/android/pt-PT/` (fastlane layout, so they can be uploaded
by hand or with `fastlane supply`). The app is only in European Portuguese, so pt-PT is the
default (and only) listing language.

| File | Play Console field | Limit |
|---|---|---|
| `title.txt` | App name | 30 |
| `short_description.txt` | Short description | 80 |
| `full_description.txt` | Full description | 4000 |
| `changelogs/<versionCode>.txt` | Release notes ("What's new") | 500 |
| `images/icon.png` | App icon (512×512) | |
| `images/featureGraphic.png` | Feature graphic (1024×500) | |
| `images/phoneScreenshots/01–06.png` | Phone screenshots (1080×1920) | 2–8 |

Screenshots are framed (navy background + caption) because Play rejects images whose long side is
more than twice the short one, and the raw captures are 1280×2856. They use fictional data; the
homes shown in "Respostas" are made up, the others come from the Carta Social.

The upload is the App Bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
Each version is then copied by hand to `dist/` (not versioned): `dist/Avus-X.Y.Z.aab` for Play and
`dist/Avus-X.Y.Z-{arm64-v8a,armeabi-v7a,universal}.apk`, the files attached to the GitHub release
(`gh release create vX.Y.Z -R cruz-py/Avus-app dist/Avus-X.Y.Z-*.apk SHA256SUMS.txt`).

## Before the first upload

- **Testing requirement.** Personal developer accounts created after November 2023 must run a
  closed test with at least 12 testers, opted in for 14 days in a row, before production access.
- **App signing.** The GitHub APKs are signed with `avus-release.jks`. If Play generates its own
  app signing key (the default), Play installs and GitHub APKs cannot update each other: users
  would have to uninstall (and lose their data) to switch. To keep one signature, choose
  "Use a different key" / "Export and upload a key from Java keystore" during Play App Signing
  setup and upload `avus-release.jks` (Play gives the PEPK tool and command). Then a new upload key
  can be created for future uploads. This choice cannot be changed later.

## Store settings

- **App or game:** App · **Free**
- **Category:** Lifestyle (Estilo de vida)
- **Tags:** family, organisation/productivity related tags as offered
- **Contact email:** cruz.py27@gmail.com (shown publicly on the listing)
- **Website:** https://github.com/cruz-py/Avus-app
- **Privacy policy:** https://github.com/cruz-py/Avus-app/blob/main/PRIVACIDADE.md
- **Countries:** Portugal (the list covers mainland Portugal only)

## App content

- **Privacy policy:** URL above.
- **Ads:** No, the app contains no ads.
- **App access:** All functionality is available without special access (no login).
- **Content rating (IARC):** category "Utility, Productivity, Communication or Other". No violence,
  sexual content, profanity, drugs, gambling. Users do not interact with each other in the app,
  no user-generated content is shared through the app, it does not share the user's location,
  no digital purchases. Expected rating: PEGI 3 / Everyone.
- **Target audience:** 18 and over only. Not designed for children.
- **News app:** No.
- **Government app:** No. The app is not affiliated with the Segurança Social, DGSSS or any care home
  (the listing says so).
- **Financial features:** None.
- **Health:** The app is not a health or medical app. It only stores, on the phone, the health
  details the family types into the resident's form so they go into the application letter. If
  the Health apps declaration asks, select none of the health features (or the closest
  non-medical option) and explain this.

## Data safety

Answer **"No"** to "Does your app collect or share any of the required user data types?".

Reasoning, in case Play asks: nothing the user enters leaves the phone through the app. Emails are
composed and sent by the user's own email app (an intent), with the content the user sees there.
Distances are worked out on the phone. The map downloads OpenStreetMap tiles from OpenFreeMap for
the area on screen; those requests carry no user data (only what any web request carries).
There are no accounts, analytics, crash reporting or ads SDKs.

- **Data encrypted in transit:** not applicable (no user data is transmitted).
- **Users can request deletion:** data is deleted in the app (Definições → "Apagar os meus dados")
  or by uninstalling.
