package se.nymberg.matverktyg

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import se.nymberg.matverktyg.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Vattenpass-fliken. Auto-detekterar hur telefonen hålls (platt / på kortsida
 * / på långsida) och mäter i kant-lägena KANTENS lutning mot horisonten.
 * Felmarginal skattas ur sensorbruset. Regelpanelerna (tak/våtrum/toleranser)
 * togglas per panel och bär källor — se [RULE_PANELS].
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

    // Brusskattning: ringbuffert med senaste tilt-värdena.
    private val recent = FloatArray(25)
    private var recentCount = 0
    private var recentIdx = 0

    /** status-TextView per regel, panelvis; container per panel för toggle. */
    private val ruleStatus = HashMap<String, ArrayList<TextView>>()
    private val panelBodies = HashMap<String, LinearLayout>()

    fun init() {
        sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.also { usingGravity = true }
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        buildPanels()

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
        if (az > 0.75f) {
            val raw = Math.toDegrees(acos(az.coerceIn(0f, 1f).toDouble())).toFloat()
            tilt = (raw - prefs.offTilt).coerceAtLeast(0f)
            binding.modeLabel.setText(R.string.mode_flat_hint)
            binding.bubble.setLevel(-gx / norm - prefs.offNx, gy / norm - prefs.offNy, tilt)
        } else if (ay > ax) {
            tilt = Math.toDegrees(asin(ax.coerceIn(0f, 1f).toDouble())).toFloat()
            binding.modeLabel.setText(R.string.mode_edge_short_hint)
            binding.bubble.setLevel((gx / norm), 0f, tilt)
        } else {
            tilt = Math.toDegrees(asin(ay.coerceIn(0f, 1f).toDouble())).toFloat()
            binding.modeLabel.setText(R.string.mode_edge_long_hint)
            binding.bubble.setLevel(0f, (gy / norm), tilt)
        }

        // Brus → felmarginal
        recent[recentIdx] = tilt
        recentIdx = (recentIdx + 1) % recent.size
        if (recentCount < recent.size) recentCount++
        val margin = marginDeg()

        binding.angle.text = activity.getString(R.string.angle_deg, tilt)
        binding.angleSub.text = subText(tilt)
        binding.angleMargin.text = activity.getString(R.string.margin_deg, margin)
        updatePanels(tilt)
    }

    /** ±(2·stddev + nollterm), klampat till [0,1°..0,9°]. */
    private fun marginDeg(): Float {
        if (recentCount < 5) return 0.5f
        var mean = 0f
        for (i in 0 until recentCount) mean += recent[i]
        mean /= recentCount
        var v = 0f
        for (i in 0 until recentCount) {
            val d = recent[i] - mean
            v += d * d
        }
        val std = sqrt(v / (recentCount - 1))
        val zeroTerm = if (prefs.isZeroed) 0.05f else 0.3f
        return (2f * std + zeroTerm).coerceIn(0.1f, 0.9f)
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

    // --- Regelpaneler ---

    private fun buildPanels() {
        val d = activity.resources.displayMetrics.density
        val pad = (12 * d).roundToInt()
        for (panel in RULE_PANELS) {
            // Panelhuvud: titel + toggle
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, pad, 0, 0)
            }
            val title = TextView(activity).apply {
                text = panel.title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val toggle = MaterialSwitch(activity).apply {
                isChecked = prefs.panelEnabled(panel.id)
            }
            header.addView(title)
            header.addView(toggle)
            binding.panelsContainer.addView(header)

            // Panelkropp: undertext + regelrader
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (prefs.panelEnabled(panel.id)) View.VISIBLE else View.GONE
            }
            val subtitle = TextView(activity).apply {
                text = panel.subtitle
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(Color.parseColor("#5A5A50"))
                setPadding(0, (2 * d).roundToInt(), 0, (4 * d).roundToInt())
            }
            body.addView(subtitle)

            val statusList = ArrayList<TextView>()
            for (rule in panel.rules) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, pad / 2, 0, pad / 2)
                }
                val texts = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                texts.addView(TextView(activity).apply {
                    text = rule.name
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                })
                texts.addView(TextView(activity).apply {
                    text = rule.note
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(Color.parseColor("#5A5A50"))
                })
                texts.addView(TextView(activity).apply {
                    text = activity.getString(R.string.panel_source, rule.source.label)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                    setTextColor(Color.parseColor("#9B968A"))
                })
                val status = TextView(activity).apply {
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                }
                row.addView(texts)
                row.addView(status)
                body.addView(row)
                statusList.add(status)
            }
            binding.panelsContainer.addView(body)
            ruleStatus[panel.id] = statusList
            panelBodies[panel.id] = body

            toggle.setOnCheckedChangeListener { _, on ->
                prefs.setPanelEnabled(panel.id, on)
                body.visibility = if (on) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updatePanels(tilt: Float) {
        for (panel in RULE_PANELS) {
            if (panelBodies[panel.id]?.visibility != View.VISIBLE) continue
            val statusList = ruleStatus[panel.id] ?: continue
            for (i in panel.rules.indices) {
                val r = panel.rules[i]
                val okMin = r.minDeg == null || tilt >= r.minDeg
                val okMax = r.maxDeg == null || tilt <= r.maxDeg
                val ok = okMin && okMax
                statusList[i].apply {
                    text = when {
                        ok -> activity.getString(R.string.suitable)
                        !okMin -> activity.getString(R.string.needs_more, r.minDeg)
                        else -> activity.getString(R.string.over_max, r.maxDeg)
                    }
                    setTextColor(if (ok) Color.parseColor("#5E7D5A") else Color.parseColor("#9B968A"))
                }
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(activity, m, Toast.LENGTH_SHORT).show()
}
