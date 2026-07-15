package se.nymberg.matverktyg

import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.nymberg.matverktyg.databinding.ActivityMainBinding
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Linjal-fliken. Kalibrering i tre nivåer:
 *  1. Modelldatabas (visas som "Kalibrerad automatiskt: <modell>")
 *  2. Skärmdiagonal i tum → px/mm ur verklig upplösning
 *  3. Finjustering mot bankkort
 * Felmarginalen visas i mätraden och beror på kalibreringskällan.
 */
class RulerController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) {
    private val prefs = RulerPrefs(activity)

    private var knownDevice: DeviceCalibration.KnownDevice? = null
    private var autoPxPerMm: Float = 10f

    fun init() {
        knownDevice = DeviceCalibration.lookup()
        autoPxPerMm = knownDevice?.pxPerMm ?: systemEstimate()
        binding.ruler.pxPerMm = effectivePxPerMm()

        binding.ruler.onMeasureChanged = { mm -> showMeasurement(mm) }
        binding.ruler.onCalibrationChanged = { v ->
            binding.calSeek.progress = progressFor(v)
            updateCalReadout(v)
        }

        binding.checkButton.setOnClickListener {
            val on = !binding.ruler.checkMode
            binding.ruler.checkMode = on
            binding.readout.text =
                if (on) activity.getString(R.string.check_hint)
                else sourceLine()
        }
        binding.calibrateButton.setOnClickListener { enterCalibration() }
        binding.clearButton.setOnClickListener {
            binding.ruler.clearMarker()
            binding.ruler.checkMode = false
            binding.readout.text = sourceLine()
        }
        binding.calDoneButton.setOnClickListener { exitCalibration(save = true) }
        binding.calResetButton.setOnClickListener {
            prefs.clear()
            binding.ruler.pxPerMm = autoPxPerMm
            binding.calSeek.progress = progressFor(autoPxPerMm)
            updateCalReadout(autoPxPerMm)
        }
        binding.diagonalButton.setOnClickListener { askDiagonal() }
        binding.calSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = pxPerMmFor(progress)
                binding.ruler.pxPerMm = v
                updateCalReadout(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        binding.readout.text = sourceLine()
    }

    private fun effectivePxPerMm(): Float =
        if (prefs.isCalibrated) prefs.pxPerMm else autoPxPerMm

    /** Beskriver aktuell kalibreringskälla för användaren. */
    private fun sourceLine(): String = when {
        prefs.isCalibrated && prefs.source == "diagonal" ->
            activity.getString(R.string.calibrated_diagonal)
        prefs.isCalibrated ->
            activity.getString(R.string.calibrated_card)
        knownDevice != null ->
            activity.getString(R.string.calibrated_auto, knownDevice!!.displayName)
        else ->
            activity.getString(R.string.calibrated_estimate)
    }

    /** Felmarginal i mm för en mätning av längd L, beroende på källa. */
    private fun marginMm(lengthMm: Float): Float = when {
        prefs.isCalibrated -> max(0.5f, 0.005f * lengthMm)       // kort/diagonal
        knownDevice != null -> max(0.5f, 0.01f * lengthMm)       // modelldatabas
        else -> 0.10f * lengthMm                                  // ren uppskattning
    }

    /** ydpi är ofta felrapporterad — startgissning, kalibrering rättar. */
    private fun systemEstimate(): Float {
        val dm = activity.resources.displayMetrics
        val ydpi = if (dm.ydpi > 1f) dm.ydpi else dm.densityDpi.toFloat()
        return (ydpi / 25.4f).coerceIn(RulerView.MIN_PX_PER_MM, RulerView.MAX_PX_PER_MM)
    }

    private fun showMeasurement(mm: Float) {
        val cm = mm / 10f
        val inch = mm / 25.4f
        val margin = marginMm(mm)
        var text = activity.getString(R.string.measurement, cm, mm, inch) +
            "  (±" + (if (margin < 10f) String.format("%.1f", margin) else String.format("%.0f", margin)) + " mm)"
        // Nära skalans slut? Tipsa om foto-fliken för längre mått.
        val rulerLengthMm = binding.ruler.height / binding.ruler.pxPerMm
        if (rulerLengthMm > 0 && mm > rulerLengthMm - 8f) {
            text += "\n" + activity.getString(R.string.ruler_too_long_hint)
        }
        binding.readout.text = text
    }

    // --- Kalibrering ---

    private fun enterCalibration() {
        binding.calSeek.progress = progressFor(binding.ruler.pxPerMm)
        updateCalReadout(binding.ruler.pxPerMm)
        binding.ruler.checkMode = false
        binding.ruler.calibrationMode = true
        binding.normalBar.visibility = android.view.View.GONE
        binding.calibrationBar.visibility = android.view.View.VISIBLE
        binding.readout.setText(R.string.calib_hint)
    }

    private fun exitCalibration(save: Boolean) {
        if (save) {
            prefs.pxPerMm = binding.ruler.pxPerMm
            prefs.source = "card"
        }
        binding.ruler.calibrationMode = false
        binding.calibrationBar.visibility = android.view.View.GONE
        binding.normalBar.visibility = android.view.View.VISIBLE
        binding.readout.text = sourceLine()
    }

    /** Nivå 2: användaren anger skärmdiagonalen i tum. */
    private fun askDiagonal() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = activity.getString(R.string.diagonal_hint)
        }
        val container = FrameLayout(activity).apply {
            val pad = (20 * activity.resources.displayMetrics.density).roundToInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.enter_diagonal)
            .setView(container)
            .setPositiveButton(R.string.done) { _, _ ->
                val diag = input.text.toString().replace(',', '.').toFloatOrNull()
                if (diag != null && diag in 3f..15f) {
                    val diagPx = realDiagonalPx()
                    val v = (diagPx / (diag * 25.4)).toFloat()
                        .coerceIn(RulerView.MIN_PX_PER_MM, RulerView.MAX_PX_PER_MM)
                    prefs.pxPerMm = v
                    prefs.source = "diagonal"
                    binding.ruler.pxPerMm = v
                    binding.calSeek.progress = progressFor(v)
                    updateCalReadout(v)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Skärmens verkliga diagonal i pixlar (API 30+/29-grenar). */
    private fun realDiagonalPx(): Double {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = activity.windowManager.maximumWindowMetrics.bounds
            hypot(b.width().toDouble(), b.height().toDouble())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(dm)
            hypot(dm.widthPixels.toDouble(), dm.heightPixels.toDouble())
        }
    }

    private fun updateCalReadout(pxPerMm: Float) {
        val dpi = pxPerMm * 25.4f
        binding.calReadout.text =
            activity.getString(R.string.calib_readout, pxPerMm, dpi) +
                "\n" + DeviceCalibration.identity()
        binding.calSource.text = sourceLine()
    }

    private fun pxPerMmFor(progress: Int): Float =
        RulerView.MIN_PX_PER_MM +
            (progress / 1000f) * (RulerView.MAX_PX_PER_MM - RulerView.MIN_PX_PER_MM)

    private fun progressFor(pxPerMm: Float): Int =
        (((pxPerMm - RulerView.MIN_PX_PER_MM) /
            (RulerView.MAX_PX_PER_MM - RulerView.MIN_PX_PER_MM)) * 1000f)
            .roundToInt().coerceIn(0, 1000)
}
