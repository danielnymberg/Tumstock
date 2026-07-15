package se.nymberg.matverktyg

import android.content.Context

/** Nollnings-offset + panelval, lagras lokalt. Inget nätverk. */
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

    val isZeroed: Boolean
        get() = sp.contains("off_tilt")

    fun clear() {
        sp.edit().remove("off_tilt").remove("off_nx").remove("off_ny").apply()
    }

    /** Är regelpanelen påslagen? Tak är på som standard. */
    fun panelEnabled(id: String): Boolean =
        sp.getBoolean("panel_$id", id == "roof")

    fun setPanelEnabled(id: String, on: Boolean) {
        sp.edit().putBoolean("panel_$id", on).apply()
    }
}
