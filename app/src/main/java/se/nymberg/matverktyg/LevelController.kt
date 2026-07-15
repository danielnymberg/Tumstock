package se.nymberg.matverktyg

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import se.nymberg.matverktyg.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Vattenpass-fliken. Läser gravitationssensorn och AUTO-detekterar hur
 * telefonen hålls:
 *
 * - PLATT (ryggen mot ytan): lutning = vinkeln mellan ryggen och
 *   horisontalplanet, acos(|gz|/g). 0° = plant.
 * - PÅ KANT: telefonen står på en kant (kamerapuckel/sidoknappar hindrar
 *   platt läge). Då mäts KANTENS lutning mot horisonten — den axel som
 *   ligger LÄNGS underlaget — inte hur upprest telefonen är:
 *     står på kortsidan  → kanten är x-axeln → lutning = asin(|gx|/g)
 *     ligger på långsidan → kanten är y-axeln → lutning = asin(|gy|/g)
 *   På plan yta ger detta 0°, på ett 20°-tak 20°.
 */
class LevelController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) : SensorEventListener {

    private val prefs = LevelPrefs(activity)
    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private var usingGravity = false

    private val gravity = FloatArray(3)
    private var frozen = false

    private val roofStatus = ArrayList<TextView>()

    fun init() {
        sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.also { usingGravity = true }
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        buildRoofingRows()

        binding.freezeButton.setOnClickListener {
            frozen = !frozen
            binding.freezeButton.setText(if (frozen) R.string.unfreeze else R.string.freeze)
        }
        binding.zeroButton.setOnClickListener { zeroToCurrent() }
        binding.zeroButton.setOnLongClickListener {
            prefs.clear()
            toast(activity.getString(R.string.zero_reset))
            true
        }
    }

    fun resume() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun pause() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (usingGravity) {
            gravity[0] = event.values[0]
            gravity[1] = event.values[1]
            gravity[2] = event.values[2]
        } else {
            val a = 0.8f
            gravity[0] = a * gravity[0] + (1 - a) * event.values[0]
            gravity[1] = a * gravity[1] + (1 - a) * event.values[1]
            gravity[2] = a * gravity[2] + (1 - a) * event.values[2]
        }
        if (!frozen) update()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun update() {
        val gx = gravity[0]
        val gy = gravity[1]
        val gz = gravity[2]
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (norm < 1e-3f) return

        val ax = abs(gx) / norm
        val ay = abs(gy) / norm
        val az = abs(gz) / norm

        val tilt: Float
        val flatMode = az > 0.75f
        if (flatMode) {
            // Platt: ryggens avvikelse från horisontalplanet.
            val raw = Math.toDegrees(acos(az.coerceIn(0f, 1f).toDouble())).toFloat()
            tilt = (raw - prefs.offTilt).coerceAtLeast(0f)
            binding.modeLabel.setText(R.string.mode_flat_hint)
            binding.bubble.visibility = android.view.View.VISIBLE
            binding.bubble.setLevel(-gx / norm - prefs.offNx, gy / norm - prefs.offNy, tilt)
        } else if (ay > ax) {
            // Står på kortsidan: kanten (x-axeln) mot horisonten.
            tilt = Math.toDegrees(asin(ax.coerceIn(0f, 1f).toDouble())).toFloat()
            binding.modeLabel.setText(R.string.mode_edge_short_hint)
            binding.bubble.visibility = android.view.View.VISIBLE
            binding.bubble.setLevel((gx / norm), 0f, tilt)
        } else {
            // Ligger på långsidan: kanten (y-axeln) mot horisonten.
            tilt = Math.toDegrees(asin(ay.coerceIn(0f, 1f).toDouble())).toFloat()
            binding.modeLabel.setText(R.string.mode_edge_long_hint)
            binding.bubble.visibility = android.view.View.VISIBLE
            binding.bubble.setLevel(0f, (gy / norm), tilt)
        }

        binding.angle.text = activity.getString(R.string.angle_deg, tilt)
        binding.angleSub.text = subText(tilt)
        updateRoofing(tilt)
    }

    private fun subText(tiltDeg: Float): String {
        val rad = Math.toRadians(tiltDeg.toDouble())
        val percent = (tan(rad) * 100).toFloat()
        return if (tiltDeg < 0.05f) {
            activity.getString(R.string.sub_level)
        } else {
            val ratio = if (tan(rad) > 1e-4) 1.0 / tan(rad) else 0.0
            activity.getString(R.string.sub_grade, percent, ratio.toFloat())
        }
    }

    private fun zeroToCurrent() {
        val gx = gravity[0]; val gy = gravity[1]; val gz = gravity[2]
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (norm < 1e-3f) return
        prefs.offTilt = Math.toDegrees(
            acos((abs(gz) / norm).coerceIn(0f, 1f).toDouble())
        ).toFloat()
        prefs.offNx = -gx / norm
        prefs.offNy = gy / norm
        toast(activity.getString(R.string.zeroed))
    }

    private fun buildRoofingRows() {
        val pad = (12 * activity.resources.displayMetrics.density).roundToInt()
        for (r in ROOFINGS) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, pad, 0, pad)
                gravity = Gravity.CENTER_VERTICAL
            }
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val name = TextView(activity).apply {
                text = r.name
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            }
            val note = TextView(activity).apply {
                text = activity.getString(R.string.min_pitch, r.minDeg) + " · " + r.note
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(Color.parseColor("#5A5A50"))
            }
            texts.addView(name)
            texts.addView(note)
            val status = TextView(activity).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            }
            row.addView(texts)
            row.addView(status)
            binding.roofingContainer.addView(row)
            roofStatus.add(status)
        }
    }

    private fun updateRoofing(tilt: Float) {
        for (i in ROOFINGS.indices) {
            val r = ROOFINGS[i]
            val ok = tilt >= r.minDeg
            roofStatus[i].apply {
                text = if (ok) activity.getString(R.string.suitable)
                       else activity.getString(R.string.needs_more, r.minDeg)
                setTextColor(if (ok) Color.parseColor("#5E7D5A") else Color.parseColor("#9B968A"))
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(activity, m, Toast.LENGTH_SHORT).show()
}
