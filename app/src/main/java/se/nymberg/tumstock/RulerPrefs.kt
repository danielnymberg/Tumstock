package se.nymberg.tumstock

import android.content.Context

/**
 * Lagrar kalibreringen (pixlar per millimeter) lokalt. Ingen lagring lämnar
 * enheten — appen har ingen nätverksbehörighet.
 */
class RulerPrefs(context: Context) {
    private val sp = context.getSharedPreferences("tumstock", Context.MODE_PRIVATE)

    /** Kalibrerat värde. 0 = ej kalibrerat (använd systemets uppskattning). */
    var pxPerMm: Float
        get() = sp.getFloat("px_per_mm", 0f)
        set(v) = sp.edit().putFloat("px_per_mm", v).apply()

    val isCalibrated: Boolean
        get() = pxPerMm > 0f
}
