package se.nymberg.tumstock

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import se.nymberg.tumstock.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: RulerPrefs

    /** Systemets uppskattning (används tills användaren kalibrerar). */
    private var systemPxPerMm: Float = 10f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = RulerPrefs(this)

        systemPxPerMm = systemEstimate()
        binding.ruler.pxPerMm = effectivePxPerMm()

        binding.ruler.onMeasureChanged = { mm -> showMeasurement(mm) }

        binding.calibrateButton.setOnClickListener { enterCalibration() }
        binding.clearButton.setOnClickListener {
            binding.ruler.clearMarker()
            binding.readout.setText(R.string.drag_hint)
        }
        binding.calDoneButton.setOnClickListener { exitCalibration(save = true) }
        binding.calResetButton.setOnClickListener {
            prefs.pxPerMm = 0f
            binding.ruler.pxPerMm = systemPxPerMm
            binding.calSeek.progress = progressFor(systemPxPerMm)
            updateCalReadout(systemPxPerMm)
        }
        binding.calSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val v = pxPerMmFor(progress)
                binding.ruler.pxPerMm = v
                updateCalReadout(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun effectivePxPerMm(): Float =
        if (prefs.isCalibrated) prefs.pxPerMm else systemPxPerMm

    /** ydpi är ofta felrapporterad — bra nog som startgissning, kalibrering rättar. */
    private fun systemEstimate(): Float {
        val dm = resources.displayMetrics
        val ydpi = if (dm.ydpi > 1f) dm.ydpi else dm.densityDpi.toFloat()
        return (ydpi / 25.4f).coerceIn(MIN_PX_PER_MM, MAX_PX_PER_MM)
    }

    private fun showMeasurement(mm: Float) {
        val cm = mm / 10f
        val inch = mm / 25.4f
        binding.readout.text = getString(R.string.measurement, cm, mm, inch)
    }

    // --- Kalibrering ---

    private fun enterCalibration() {
        binding.calSeek.progress = progressFor(binding.ruler.pxPerMm)
        updateCalReadout(binding.ruler.pxPerMm)
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
        binding.calReadout.text = getString(R.string.calib_readout, pxPerMm, dpi)
    }

    private fun pxPerMmFor(progress: Int): Float =
        MIN_PX_PER_MM + (progress / 1000f) * (MAX_PX_PER_MM - MIN_PX_PER_MM)

    private fun progressFor(pxPerMm: Float): Int =
        (((pxPerMm - MIN_PX_PER_MM) / (MAX_PX_PER_MM - MIN_PX_PER_MM)) * 1000f)
            .roundToInt().coerceIn(0, 1000)

    companion object {
        private const val MIN_PX_PER_MM = 2f
        private const val MAX_PX_PER_MM = 40f
    }
}
