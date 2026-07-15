package se.nymberg.matverktyg

import android.content.Context

/**
 * Lagrar kalibreringen (pixlar per millimeter) och dess källa lokalt.
 * Inget lämnar enheten — appen har ingen nätverksbehörighet.
 */
class RulerPrefs(context: Context) {
    private val sp = context.getSharedPreferences("tumstock", Context.MODE_PRIVATE)

    /** Kalibrerat värde. 0 = ej kalibrerat (använd auto). */
    var pxPerMm: Float
        get() = sp.getFloat("px_per_mm", 0f)
        set(v) = sp.edit().putFloat("px_per_mm", v).apply()

    /** Hur värdet kom till: "card" eller "diagonal". Tom = ej kalibrerad. */
    var source: String
        get() = sp.getString("px_source", "") ?: ""
        set(v) = sp.edit().putString("px_source", v).apply()

    val isCalibrated: Boolean
        get() = pxPerMm > 0f

    fun clear() {
        sp.edit().remove("px_per_mm").remove("px_source").apply()
    }
}
