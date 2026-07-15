package se.nymberg.matverktyg

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

    /** När true ritas en centrerad kortram ovanpå skalan — kontrolläge. */
    var checkMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Anropas när mätlinjen flyttas; ger avståndet från toppen i mm. */
    var onMeasureChanged: ((mm: Float) -> Unit)? = null

    /** Anropas i kalibreringsläge när användaren trycker och sätter nytt pxPerMm. */
    var onCalibrationChanged: ((pxPerMm: Float) -> Unit)? = null

    private var markerY: Float = -1f

    private val cardFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#B04A3A")
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(9f), dp(6f)), 0f)
    }
    private val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#14B04A3A")
    }
    private val topRef = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(3f)
        color = Color.parseColor("#1565C0")
    }
    private val cardLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B04A3A")
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
    }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1.2f)
        color = Color.parseColor("#333333")
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(2f)
        color = Color.parseColor("#B04A3A")
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
        if (checkMode) drawCheckCard(canvas)
        drawMarker(canvas)
    }

    /**
     * Kontrolläge: en kortstor ram mitt på skärmen. Lägg ett bankkort i ramen
     * — fyller kortet ramen exakt är kalibreringen rätt.
     */
    private fun drawCheckCard(canvas: Canvas) {
        val w = CARD_WID_MM * pxPerMm
        val h = CARD_LEN_MM * pxPerMm
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        val radius = dp(10f)
        canvas.drawRoundRect(left, top, left + w, top + h, radius, radius, cardFill)
        canvas.drawRoundRect(left, top, left + w, top + h, radius, radius, cardFrame)
    }

    /**
     * Kalibreringsguide. En kortformad ram (ISO-kort 85,6 × 54 mm, stående)
     * hänger från toppen (blå linje). Kortets ÖVERKANT läggs mot blå linjen;
     * användaren TRYCKER där kortets NEDERKANT är — då blir [pxPerMm] rätt.
     * Ramen ritas alltid utifrån aktuellt [pxPerMm], så efter en tryckning
     * ligger ramens nederkant exakt där man tryckte.
     */
    private fun drawCalibrationCard(canvas: Canvas) {
        val topY = dp(2f)
        val w = CARD_WID_MM * pxPerMm
        val h = CARD_LEN_MM * pxPerMm
        val left = (width - w) / 2f
        val bottomY = topY + h
        val radius = dp(10f)

        canvas.drawLine(0f, topY, width.toFloat(), topY, topRef)
        canvas.drawRoundRect(left, topY, left + w, bottomY, radius, radius, cardFill)
        canvas.drawRoundRect(left, topY, left + w, bottomY, radius, radius, cardFrame)
        canvas.drawText(context.getString(R.string.calib_card_top), width / 2f, topY + dp(20f), cardLabel)
        canvas.drawText(context.getString(R.string.calib_card_hint), width / 2f, bottomY + dp(26f), cardLabel)
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
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (calibrationMode) {
                    // Tryckpunkten = kortets nederkant, 85,6 mm under toppen.
                    val v = ((event.y - dp(2f)) / CARD_LEN_MM).coerceIn(MIN_PX_PER_MM, MAX_PX_PER_MM)
                    pxPerMm = v
                    onCalibrationChanged?.invoke(v)
                    return true
                }
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
        const val CARD_LEN_MM = 85.60f   // ISO-1-kortets långsida
        const val CARD_WID_MM = 53.98f   // ISO-1-kortets kortsida
        const val MIN_PX_PER_MM = 2f
        const val MAX_PX_PER_MM = 40f

        fun roundHalf(v: Float): Float = (v * 2).roundToInt() / 2f
    }
}
