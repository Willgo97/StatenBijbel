# StatenBijbel

Android-app met de Statenvertaling (editie 1888), alle kanttekeningen en het
kerkboek: psalmberijming van 1773, gezangen, Drie Formulieren van Enigheid,
liturgische formulieren en gebeden. Volledig offline, zoeken incluis.

**Download:** <https://github.com/Willgo97/StatenBijbel/releases/latest/download/StatenBijbel.apk>
(Android 8.0+). Hang bij elke release ook een kopie als `StatenBijbel.apk`
aan, anders breekt deze vaste link.

## Bouwen

JDK 17 en Android SDK 36.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Ondertekenen gaat via `app/keystore.properties` (zie
`keystore.properties.voorbeeld`); zonder dat bestand komt er een onondertekende APK uit.

## Data

`bijbel.db` wordt gebouwd door `tools/build_db.py` en meegeleverd in
`app/src/main/assets/`:

```bash
python3 tools/haal_extras.py ophalen && python3 tools/haal_extras.py ontleden
python3 tools/build_db.py STV.xml
cp bijbel.db app/src/main/assets/bijbel.db
```

- Bijbeltekst en kanttekeningen: <https://github.com/Isidore-Guild/statenvertaling> (CC0).
  Het script hernummert de verzen van KJV- naar SV-telling.
- Kerkboek: bijbel-statenvertaling.com; de teksten zijn publiek domein.

## Nog niet gedaan

- Korte inhoud boven elk hoofdstuk
- Apocriefe boeken (`STVA.xml` in de bron)
- Echt doorlopende tekst
