# Tumstock

En linjal på skärmen i **verklig storlek** — centimeter på ena kanten, tum på den andra. Dra fingret för att mäta. Ingen reklam, ingen spårning, **ingen nätverksbehörighet alls**.

## Varför kalibrering behövs

Android rapporterar ofta fel skärmtäthet (`xdpi`/`ydpi` kan slå fel med tiotals procent), så för millimeterprecision kalibrerar du en gång mot ett verkligt föremål:

1. Tryck **Kalibrera** — en streckad, kortformad ram visas.
2. Lägg ett **bankkort/ID-kort** på skärmen ovanpå ramen (alla ID-1-kort är 85,6 × 54 mm).
3. Dra reglaget tills ramen har **exakt samma storlek** som kortet.
4. **Klar.** Kalibreringen sparas lokalt.

Efter kalibrering är noggrannheten typiskt inom ±0,5 mm. **Återställ** går tillbaka till systemets uppskattning.

> Integritetsnot: appen läser aldrig något unikt enhets-ID. Kalibreringen lagras bara som ett tal (pixlar per millimeter) i appens egna inställningar.

## Så här installerar du

APK:n byggs automatiskt i molnet av GitHub Actions — du behöver inte Android Studio.

1. Öppna repots **Releases** → **Tumstock (senaste bygget)**.
2. Ladda ner `tumstock.apk` på telefonen.
3. Öppna filen → tillåt *"Installera okända appar"* → **Installera**.

## Bygga själv

```bash
./build.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Teknik

- Kotlin + ViewBinding (Material 3), minSdk 26, targetSdk 35.
- `RulerView`: en `Canvas`-ritad vy som graderar utifrån `pixlar per millimeter`.
- Kalibrering mot känd referens (kreditkort 85,6 mm) sparad i `SharedPreferences`.
- Noll behörigheter i manifestet. Ingen `INTERNET` → kan inte skicka data.

## Framtida förbättring

En inbäddad databas `Build.MODEL → sann DPI` kan ge automatisk kalibrering för kända modeller (±1 %), med kreditkorts-metoden som reserv för okända enheter.
