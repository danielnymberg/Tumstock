package se.nymberg.matverktyg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Rund libell (vattenpass-bubbla). [setLevel] tar normaliserad lutning i x/y
 * ([-1, 1]); bubblan förskjuts mot den höga sidan. Grön när ytan är i våg.
 */
class BubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var nx = 0f
    private var ny = 0f

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#9B968A")
    }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#9B968A")
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1f)
        color = Color.parseColor("#D8D2C6")
    }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#5E7D5A")
    }

    /** Tolerans i grader för "i våg" (grön bubbla). */
    private val levelTolerance = 0.5f
    private var tiltDeg = 0f

    fun setLevel(nx: Float, ny: Float, tiltDeg: Float) {
        this.nx = nx.coerceIn(-1f, 1f)
        this.ny = ny.coerceIn(-1f, 1f)
        this.tiltDeg = tiltDeg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - dp(8f)

        canvas.drawLine(cx - r, cy, cx + r, cy, cross)
        canvas.drawLine(cx, cy - r, cx, cy + r, cross)
        canvas.drawCircle(cx, cy, r, ring)

        val bubbleR = dp(26f)
        val targetR = bubbleR + dp(4f)
        canvas.drawCircle(cx, cy, targetR, center)

        // Bubblan rör sig mot den höga sidan (motsatt gravitationens dragriktning).
        val maxOffset = r - bubbleR
        val bx = cx + nx * maxOffset
        val by = cy + ny * maxOffset

        bubble.color = if (tiltDeg <= levelTolerance)
            Color.parseColor("#5E7D5A")   // salvia: i våg
        else
            Color.parseColor("#B5793C")   // dämpad amber: lutar
        canvas.drawCircle(bx, by, bubbleR, bubble)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        fun magnitude(x: Float, y: Float): Float = sqrt(x * x + y * y)
    }
}
