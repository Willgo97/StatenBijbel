# StatenBijbel

Een Android-bijbelapp met de **Statenvertaling en de complete kanttekeningen**.
Alles staat in de app zelf: geen internet, geen account, geen wachten.

- **31.171 verzen** in de echte Statenvertaling-telling
- **59.385 kanttekeningen** van de Statenvertalers, met trefwoord
- **48.466 verwijzingen**, in twee richtingen doorzoekbaar
- Volledig offline, ook de zoekfunctie

## Wat het doet

**Lezen.** De tekst staat in een schreefletter, met de kanttekeningnummers als
klein superscript precies bij het woord waar ze bij horen — net als in de
gedrukte uitgave. De Godsnaam staat in klein kapitaal (H<small>EERE</small>),
door de vertalers toegevoegde woorden staan cursief.

**Kanttekeningen tussen de tekst.** Tik op een vers (of op een nummer) en de
kanttekeningen vouwen open tussen de verzen, met het trefwoord vet erboven.
Nog een tik sluit ze weer.

**Verwijzingen.** Elke verwijzing in een kanttekening is aanklikbaar en toont
de aangehaalde tekst meteen ter plekke, zonder dat je je plek kwijtraakt.
Andersom kan ook: houd een vers ingedrukt en kies *verw.* om te zien welke
kanttekeningen elders naar dit vers verwijzen. Dat laatste kent de GBS-app niet.

**Zoeken.** Doorzoek de bijbeltekst óf de kanttekeningen, eventueel beperkt tot
OT of NT. De resultaten verschijnen terwijl je typt; het laatste woord telt als
begin van een woord, dus `gena` vindt al `genade`.

**Snel ergens heen.** Tik op de titel bovenaan en typ `joh 3:16`, `ps23` of
`1kon 18` — spaties en punten maken niet uit.

**Bewaren.** Bladwijzers, markeringen in vijf kleuren en een geschiedenis van
gelezen hoofdstukken. Alles blijft op het toestel.

**Weergave.** Vijf thema's (systeem, licht, sepia, donker, nacht) en acht
accentkleuren — goud, rozerood, bordeaux, koper, blauw, groen, paars en inkt.
Het accent kleurt de kanttekeningnummers, de verwijzingen en de balk bij een
gekozen vers; elke kleur heeft een eigen variant voor lichte en donkere thema's,
zodat hij overal leesbaar blijft. Verder traploos instelbare tekstgrootte en
regelafstand, schreef of schreefloos. De statusbalk kleurt mee.

## Bediening

| Handeling | Wat er gebeurt |
|---|---|
| tik op een vers | kanttekeningen openen of sluiten |
| tik op een nummer | die kanttekening openen en uitlichten |
| tik op een verwijzing | de aangehaalde tekst tonen |
| **lang indrukken** | actiebalk: markeren, bewaren, kopiëren, delen, verwijzingen |
| knoppen onderaan | vorig / volgend hoofdstuk |

Vegen om van hoofdstuk te wisselen staat standaard **uit** en is in de
instellingen aan te zetten.

## Bouwen

Nodig: JDK 17 en de Android SDK (platform 36, build-tools 36).

```bash
export JAVA_HOME=~/.local/jdk/jdk-17.0.20.1+1
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew installRelease         # rechtstreeks op een aangesloten toestel
```

### Ondertekenen

De ondertekensleutel staat niet in deze repository. Wil je een release bouwen
die over een eerder geïnstalleerde versie heen gaat, zet dan naast de build een
`app/keystore.properties` (zie `app/keystore.properties.voorbeeld`) en je eigen
`.jks` ernaast. Zonder dat bestand bouwt `assembleRelease` gewoon door, maar
levert hij een onondertekende APK op.

Android weigert een update die met een andere sleutel is ondertekend, dus die
`.jks` is het bewaren waard — raak je hem kwijt, dan kan de app alleen nog
opnieuw geïnstalleerd worden, met verlies van bladwijzers en markeringen.

## De database

`bijbel.db` (24 MB) wordt gemaakt uit het OSIS-bestand van de Statenvertaling
en bij de eerste start uit de APK uitgepakt naar de privéopslag van de app.

```bash
python3 tools/build_db.py STV.xml            # -> bijbel.db
cp bijbel.db app/src/main/assets/bijbel.db
```

Bron: <https://github.com/Isidore-Guild/statenvertaling> — Statenvertaling
editie 1888 met de kanttekeningen, vrijgegeven onder CC0.

### Wat het bouwscript oplost

**De versnummering.** Het OSIS-bestand gebruikt de Engelse (KJV) telling, maar
de Statenvertaling volgt de Hebreeuwse: in de Psalmen is het opschrift vers 1,
en op dertien plaatsen loopt de telling anders. De echte SV-nummers staan in de
bron verstopt als `[03:2]`-markeringen midden in de tekst. Het script leest die
uit, deelt de verzen opnieuw in en vertaalt alle 48.466 verwijzingen mee. Zo
komt `Ps. 51:12` uit op *Schep mij een rein hart*, zoals het hoort — en niet op
vers 10, zoals de Engelse telling zou geven.

Eén zetfout in de bron wordt daarbij stilgezwijgend hersteld: Ps. 84:7 staat er
gemarkeerd als `[086:7]`, waardoor het vers anders in Psalm 86 zou belanden.

**De plaats van de nummers.** De kanttekeningen staan in de bron achter het vers,
met een trefwoord. Het script zoekt dat trefwoord op in de verstekst en zet het
nummer er direct achter. Dat lukt voor 58.646 van de 59.385 kanttekeningen
(98,8%); bij de rest — meestal een spellingverschil zoals *Hamaälòth* tegenover
*Hammaaloth* — komt het nummer aan het eind van het vers te staan.

**Typografie.** Verwijzingen krijgen Nederlandse afkortingen (`Psa 90:2` wordt
`Ps. 90:2`) met een onbreekbare spatie, en de spatie die de bron vóór elk
leesteken zet (`Zie Gen 1:2 .`) wordt weggehaald.

## Opzet

Geen WebView, geen HTML: de tekst wordt als platte tekst plus een compacte
`spans`-string opgeslagen en native met Compose `AnnotatedString` getekend.

```
tools/build_db.py         OSIS -> SQLite (hernummering, kanttekeningen, index)
app/src/main/assets/      bijbel.db
  .../Bijbel.kt           database, zoekindex, plaatsherkenning
  .../Render.kt           spans -> AnnotatedString
  .../Lezer.kt            leesscherm
  .../Onderdelen.kt       kanttekeningen, verwijzingen, actiebalk
  .../Schermen.kt         boeken, zoeken, bewaard, instellingen
  .../Thema.kt            kleuren en typografie
```

Zoeken gebruikt een eigen woordindex (delta-varint blobs per term), geen FTS —
zo is de database op elk Android-toestel leesbaar. Zoeken in de hele bijbel
kost enkele milliseconden.

## Nog niet gedaan

- **Korte inhoud per hoofdstuk.** De gedrukte Statenbijbel heeft boven elk
  hoofdstuk een samenvatting. Die staat niet in deze teksteditie; hij zou van
  statenvertaling.net gehaald moeten worden, en staat daar in de spelling van
  1637, wat vloekt met de rest van de tekst.
- **Apocriefe boeken.** De bron heeft ze (`STVA.xml`), de app nog niet.
- **Doorlopende tekst.** Nu staat elk vers op een eigen regel; *Compacte
  versregels* zet ze dichter op elkaar, maar echt doorlopende bladspiegel is er
  nog niet.
