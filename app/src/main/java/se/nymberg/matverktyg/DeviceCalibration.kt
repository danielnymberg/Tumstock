package se.nymberg.matverktyg

import android.os.Build

/**
 * Kända telefonmodeller → sann skärmtäthet (pixlar per millimeter), så att
 * linjalen är rätt direkt utan kalibrering. `xdpi`/`ydpi` som Android
 * rapporterar är opålitliga; de här värdena är räknade ur faktisk upplösning
 * och skärmstorlek.
 *
 * Kortkalibreringen är alltid den universella garantin — den här listan gör
 * bara att kända telefoner stämmer direkt. Lägg till fler modeller efter hand;
 * `identity()` visar strängen att matcha på.
 */
object DeviceCalibration {

    /** Returnerar sann pxPerMm för känd modell, annars null. */
    fun lookup(): Float? {
        val mfr = (Build.MANUFACTURER ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        val device = (Build.DEVICE ?: "").lowercase()

        // Nothing Phone (3): 6,67", 1260 × 2800 → ~460 ppi → 18,12 px/mm.
        if (mfr == "nothing" &&
            (model.contains("(3)") || model.contains("a024") ||
                device.contains("a024") || model.contains("phone 3"))
        ) {
            return 18.12f
        }

        return null
    }

    /** Läsbar modellsträng, för att kunna lägga till exakt matchning senare. */
    fun identity(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
}
