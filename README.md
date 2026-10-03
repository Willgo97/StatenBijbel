# StatenBijbel

Android app with the Statenvertaling (1888 edition), all marginal notes and the
church book: the 1773 metrical psalms, hymns, the Three Forms of Unity,
liturgical forms and prayers. Fully offline, search included.

**Download:** <https://github.com/Willgo97/StatenBijbel/releases/latest/download/StatenBijbel.apk>
(Android 8.0+). Attach a copy named `StatenBijbel.apk` to every release as well,
otherwise this fixed link breaks.

## Building

JDK 17 and Android SDK 36.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Signing uses `app/keystore.properties` (see
`keystore.properties.example`); without that file the APK comes out unsigned.

## Data

`bijbel.db` is built by `tools/build_db.py` and shipped in
`app/src/main/assets/`:

```bash
python3 tools/fetch_extras.py fetch && python3 tools/fetch_extras.py parse
python3 tools/build_db.py STV.xml
cp bijbel.db app/src/main/assets/bijbel.db
```

- Bible text and marginal notes: <https://github.com/Isidore-Guild/statenvertaling> (CC0).
  The script renumbers the verses from KJV to SV numbering.
- Church book: bijbel-statenvertaling.com; the texts are in the public domain.

## Not done yet

- Short summary above each chapter
- Apocryphal books (`STVA.xml` in the source)
- Truly continuous text
