# Mätverktyg — by DaNy Apps

Tre mätverktyg i en app. Ingen reklam, ingen spårning, **inga behörigheter alls** — inte ens kamera (systemkameran tar bilden åt appen) och ingen INTERNET (kan inte skicka data någonstans).

## Verktygen

### 📏 Linjal
Linjal i verklig storlek på skärmen — cm på ena kanten, tum på den andra. Dra fingret för att mäta.

- **Kända telefoner** (t.ex. Nothing Phone (3)) ställer in sig automatiskt via inbyggd modelldatabas.
- **Kontroll:** tryck *Kontroll* → en kortstor ram ritas mitt på skärmen. Lägg ett bankkort i ramen — fyller kortet ramen exakt är linjalen rätt.
- **Kalibrera:** lägg kortet med överkanten mot den blå linjen, tryck där kortets nederkant är. Sparas lokalt. (Alla ID-1-kort är 85,6 × 54 mm.)

### 📷 Foto
Mät verkliga föremål i en bild — glasögon, beslag, kvitton, borrhålsavstånd:

1. Lägg ett **bankkort** bredvid föremålet (samma plan/bordsyta).
2. Fota rakt uppifrån (*Ta foto*) eller välj en befintlig bild.
3. Markera kortets **fyra hörn** (dra för att finjustera).
4. Tryck på **två punkter** — avståndet visas i mm/cm.

Kortets kända mått ger en perspektivkorrigerad skala (homografi), så även lite sneda bilder blir rätt. Noggrannhet typiskt ±1–2 %. Gäller föremål i **samma plan** som kortet.

### 🧭 Vattenpass
Libell + vinkel i grader, % och förhållande (1:x). **Auto-detekterar** hur telefonen hålls:

- **Platt** — ligger på ytan: klassisk bubbla.
- **På kant** — står på kortsidan eller ligger på långsidan (för telefoner med kamerapuckel/sidoknappar): mäter **kantens lutning mot horisonten**. Plan yta = 0°.

**Tak-läge:** visar vilka taktäckningar som klarar uppmätt lutning (papp ~3°, bandplåt ~6°, TRP ~14°, betong/falsat tegel ~14°, ofalsat tegel ~22°, shingel ~15°). Riktvärden — bindande krav står i tillverkarens monteringsanvisning och branschregler (AMA Hus, tätskiktsgarantier).

## Installera

APK:n byggs automatiskt av GitHub Actions:

1. **Releases** → *Mätverktyg (senaste bygget)* → ladda ner `matverktyg.apk`.
2. Öppna filen → tillåt *"Installera okända appar"* → installera.

## Bygga själv

```bash
./build.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Teknik

- Kotlin + ViewBinding, Material 3 i DaNy Apps designspråk (papper/bläck), minSdk 26, targetSdk 35.
- Linjal: Canvas-skala från px/mm; modelldatabas + kortkalibrering.
- Foto: systemkamera/bildväljare → fyra korthörn → DLT-homografi (egen matte, ingen extern lib) → mm.
- Vattenpass: `TYPE_GRAVITY`, auto-läge via dominant gravitationsaxel.
- Fast signeringsnyckel → uppdateringar installeras rakt över varandra.

*En app av **DaNy Apps** · [danyapps.se](https://danyapps.se)*
