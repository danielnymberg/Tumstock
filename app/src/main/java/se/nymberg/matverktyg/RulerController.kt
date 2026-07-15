package se.nymberg.matverktyg

import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import se.nymberg.matverktyg.databinding.ActivityMainBinding
import kotlin.math.roundToInt

/** Linjal-fliken: mät, kontrollera, kalibrera. */
class RulerController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) {
    private val prefs = RulerPrefs(activity)

    /** Auto-värde: känd modell ur databasen, annars systemets uppskattning. */
    private var autoPxPerMm: Float = 10f

    fun init() {
        val dbValue = DeviceCalibration.lookup()
        autoPxPerMm = dbValue ?: systemEstimate()
        binding.ruler.pxPerMm = effectivePxPerMm()

        binding.ruler.onMeasureChanged = { mm -> showMeasurement(mm) }
        binding.ruler.onCalibrationChanged = { v ->
            binding.calSeek.progress = progressFor(v)
            updateCalReadout(v)
        }

        binding.checkButton.setOnClickListener {
            val on = !binding.ruler.checkMode
            binding.ruler.checkMode = on
            binding.readout.setText(if (on) R.string.check_hint else R.string.drag_hint)
        }
        binding.calibrateButton.setOnClickListener { enterCalibration() }
        binding.clearButton.setOnClickListener {
            binding.ruler.clearMarker()
            binding.ruler.checkMode = false
            binding.readout.setText(R.string.drag_hint)
        }
        binding.calDoneButton.setOnClickListener { exitCalibration(save = true) }
        binding.calResetButton.setOnClickListener {
            prefs.pxPerMm = 0f
            binding.ruler.pxPerMm = autoPxPerMm
            binding.calSeek.progress = progressFor(autoPxPerMm)
            updateCalReadout(autoPxPerMm)
        }
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
    }

    private fun effectivePxPerMm(): Float =
        if (prefs.isCalibrated) prefs.pxPerMm else autoPxPerMm

    /** ydpi är ofta felrapporterad — startgissning, kalibrering rättar. */
    private fun systemEstimate(): Float {
        val dm = activity.resources.displayMetrics
        val ydpi = if (dm.ydpi > 1f) dm.ydpi else dm.densityDpi.toFloat()
        return (ydpi / 25.4f).coerceIn(RulerView.MIN_PX_PER_MM, RulerView.MAX_PX_PER_MM)
    }

    private fun showMeasurement(mm: Float) {
        val cm = mm / 10f
        val inch = mm / 25.4f
        binding.readout.text = activity.getString(R.string.measurement, cm, mm, inch)
    }

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
        if (save) prefs.pxPerMm = binding.ruler.pxPerMm
        binding.ruler.calibrationMode = false
        binding.calibrationBar.visibility = android.view.View.GONE
        binding.normalBar.visibility = android.view.View.VISIBLE
        binding.readout.setText(R.string.drag_hint)
    }

    private fun updateCalReadout(pxPerMm: Float) {
        val dpi = pxPerMm * 25.4f
        binding.calReadout.text =
            activity.getString(R.string.calib_readout, pxPerMm, dpi) +
                "\n" + DeviceCalibration.identity()
    }

    private fun pxPerMmFor(progress: Int): Float =
        RulerView.MIN_PX_PER_MM +
            (progress / 1000f) * (RulerView.MAX_PX_PER_MM - RulerView.MIN_PX_PER_MM)

    private fun progressFor(pxPerMm: Float): Int =
        (((pxPerMm - RulerView.MIN_PX_PER_MM) /
            (RulerView.MAX_PX_PER_MM - RulerView.MIN_PX_PER_MM)) * 1000f)
            .roundToInt().coerceIn(0, 1000)
}
