# Tumstock

En linjal på skärmen i **verklig storlek** — centimeter på ena kanten, tum på den andra. Dra fingret för att mäta. Ingen reklam, ingen spårning, **ingen nätverksbehörighet alls**.

## Varför kalibrering behövs

Kända telefoner (t.ex. Nothing Phone (3)) ställer in sig **automatiskt** via en inbyggd modelldatabas. För övriga — eller för att kontrollera — kalibrerar du en gång mot ett bankkort (Android rapporterar ofta fel skärmtäthet, så `xdpi`/`ydpi` duger inte för mm-precision):

1. Tryck **Kalibrera** — en kortformad ram hänger från en blå linje upptill.
2. Lägg ett **bankkort** med **överkanten mot den blå linjen** (kortet stående; alla ID-1-kort är 85,6 × 54 mm).
3. **Tryck på skärmen där kortets nederkant är.** Ramen snäpper dit. Finjustera vid behov med reglaget.
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
