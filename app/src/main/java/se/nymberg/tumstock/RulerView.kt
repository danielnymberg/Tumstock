package se.nymberg.tumstock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * Ritar en linjal i verklig storlek: centimeterskala längs vänsterkanten,
 * tumskala längs högerkanten. En dragbar mätlinje visar avståndet från toppen.
 *
 * All storleksberäkning bygger på [pxPerMm] (pixlar per millimeter), som sätts
 * av aktiviteten utifrån kalibrering eller systemets skärmdata.
 */
class RulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var pxPerMm: Float = 10f
        set(value) {
            field = value
            invalidate()
        }

    /** När true ritas en kortformad kalibreringsram istället för skalan. */
    var calibrationMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Anropas när mätlinjen flyttas; ger avståndet från toppen i mm. */
    var onMeasureChanged: ((mm: Float) -> Unit)? = null

    private var markerY: Float = -1f

    private val cardFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#D32F2F")
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(9f), dp(6f)), 0f)
    }
    private val cardLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
    }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1.2f)
        color = Color.parseColor("#333333")
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(2f)
        color = Color.parseColor("#D32F2F")
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        textSize = dp(13f)
    }
    private val labelRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        textSize = dp(13f)
        textAlign = Paint.Align.RIGHT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (calibrationMode) {
            drawCalibrationCard(canvas)
            return
        }
        drawMetric(canvas)
        drawImperial(canvas)
        drawMarker(canvas)
    }

    /**
     * Ritar en kortformad, streckad ram i ISO-kortets mått (85,6 × 54 mm,
     * stående). Användaren lägger sitt kort på ramen och drar reglaget tills
     * de är exakt lika stora — då är [pxPerMm] rätt. Inga mm-streck att tolka.
     */
    private fun drawCalibrationCard(canvas: Canvas) {
        val w = 53.98f * pxPerMm   // kortets kortsida
        val h = 85.60f * pxPerMm   // kortets långsida
        val left = (width - w) / 2f
        val top = dp(96f)
        canvas.drawText(
            context.getString(R.string.calib_card_hint),
            width / 2f, top - dp(22f), cardLabel
        )
        val radius = dp(10f)
        canvas.drawRoundRect(left, top, left + w, top + h, radius, radius, cardFrame)
    }

    /** Centimeterskala längs vänsterkanten. */
    private fun drawMetric(canvas: Canvas) {
        val shortTick = dp(14f)
        val midTick = dp(26f)
        val longTick = dp(40f)
        var mm = 0
        var y = 0f
        while (y <= height) {
            y = mm * pxPerMm
            val len = when {
                mm % 10 == 0 -> longTick
                mm % 5 == 0 -> midTick
                else -> shortTick
            }
            canvas.drawLine(0f, y, len, y, line)
            if (mm % 10 == 0) {
                canvas.drawText("${mm / 10}", len + dp(4f), y + dp(5f), label)
            }
            mm++
        }
    }

    /** Tumskala längs högerkanten (1 tum = 25,4 mm), gradering var 1/16 tum. */
    private fun drawImperial(canvas: Canvas) {
        val pxPerInch = pxPerMm * 25.4f
        val pxPerSixteenth = pxPerInch / 16f
        val shortTick = dp(14f)
        val eighthTick = dp(20f)
        val quarterTick = dp(26f)
        val halfTick = dp(32f)
        val inchTick = dp(40f)
        var i = 0
        var y = 0f
        while (y <= height) {
            y = i * pxPerSixteenth
            val len = when {
                i % 16 == 0 -> inchTick
                i % 8 == 0 -> halfTick
                i % 4 == 0 -> quarterTick
                i % 2 == 0 -> eighthTick
                else -> shortTick
            }
            canvas.drawLine(width - len, y, width.toFloat(), y, line)
            if (i % 16 == 0) {
                canvas.drawText("${i / 16}\"", width - len - dp(4f), y + dp(5f), labelRight)
            }
            i++
        }
    }

    private fun drawMarker(canvas: Canvas) {
        if (markerY < 0) return
        canvas.drawLine(0f, markerY, width.toFloat(), markerY, accent)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (calibrationMode) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                markerY = event.y.coerceIn(0f, height.toFloat())
                onMeasureChanged?.invoke(markerY / pxPerMm)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Nollställ mätlinjen. */
    fun clearMarker() {
        markerY = -1f
        invalidate()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        fun roundHalf(v: Float): Float = (v * 2).roundToInt() / 2f
    }
}
