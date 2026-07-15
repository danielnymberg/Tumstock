package se.nymberg.matverktyg

import android.os.Build

/**
 * Kända telefonmodeller → sann skärmtäthet (pixlar per millimeter), så att
 * linjalen är rätt direkt utan kalibrering. `xdpi`/`ydpi` som Android
 * rapporterar är opålitliga; de här värdena är räknade ur faktisk upplösning
 * och skärmstorlek.
 */
object DeviceCalibration {

    data class KnownDevice(val pxPerMm: Float, val displayName: String)

    /** Returnerar känd modell eller null. */
    fun lookup(): KnownDevice? {
        val mfr = (Build.MANUFACTURER ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        val device = (Build.DEVICE ?: "").lowercase()

        // Nothing Phone (3): 6,67", 1260 × 2800 → ~460 ppi → 18,12 px/mm.
        if (mfr == "nothing" &&
            (model.contains("(3)") || model.contains("a024") ||
                device.contains("a024") || model.contains("phone 3"))
        ) {
            return KnownDevice(18.12f, "Nothing Phone (3)")
        }

        return null
    }

    /** Läsbar modellsträng, för att kunna lägga till exakt matchning senare. */
    fun identity(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
}
