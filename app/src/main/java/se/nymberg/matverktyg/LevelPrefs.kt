package se.nymberg.matverktyg

import android.content.Context

/** Sparar nollställnings-offset (sensorbias) lokalt. Inget nätverk. */
class LevelPrefs(context: Context) {
    private val sp = context.getSharedPreferences("vattenpass", Context.MODE_PRIVATE)

    var offTilt: Float
        get() = sp.getFloat("off_tilt", 0f)
        set(v) = sp.edit().putFloat("off_tilt", v).apply()

    var offNx: Float
        get() = sp.getFloat("off_nx", 0f)
        set(v) = sp.edit().putFloat("off_nx", v).apply()

    var offNy: Float
        get() = sp.getFloat("off_ny", 0f)
        set(v) = sp.edit().putFloat("off_ny", v).apply()

    fun clear() {
        sp.edit().remove("off_tilt").remove("off_nx").remove("off_ny").apply()
    }
}
