package se.nymberg.matverktyg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Visar ett foto (fit-center) och låter användaren tappa/dra markörpunkter:
 * först fyra korthörn (terrakotta), sedan två mätpunkter (bläck).
 * Alla punkter lagras i BILD-koordinater; vyn sköter transformationen.
 */
class PhotoMarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface Listener {
        fun onPointsChanged(corners: Int, measures: Int)
    }

    var listener: Listener? = null

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val inverse = Matrix()

    val corners = ArrayList<PointF>(4)
    val measures = ArrayList<PointF>(2)
    private var dragging: PointF? = null

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B04A3A")
        style = Paint.Style.FILL
    }
    private val cornerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2EFE8")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val quadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B04A3A")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
    }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1F1E")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1F1E")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    fun setImage(bm: Bitmap?) {
        bitmap = bm
        corners.clear()
        measures.clear()
        dragging = null
        requestLayout()
        updateMatrix()
        invalidate()
        notifyChange()
    }

    fun hasImage(): Boolean = bitmap != null

    fun undoLast() {
        when {
            measures.isNotEmpty() -> measures.removeAt(measures.size - 1)
            corners.isNotEmpty() -> corners.removeAt(corners.size - 1)
        }
        invalidate()
        notifyChange()
    }

    fun clearMeasures() {
        measures.clear()
        invalidate()
        notifyChange()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun updateMatrix() {
        val bm = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width / bm.width.toFloat(), height / bm.height.toFloat())
        val dx = (width - bm.width * scale) / 2f
        val dy = (height - bm.height * scale) / 2f
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        matrix.invert(inverse)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bm = bitmap ?: return
        canvas.drawBitmap(bm, matrix, null)

        // Kort-fyrhörningen
        if (corners.size >= 2) {
            val pts = corners.map { toView(it) }
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                if (i < pts.size - 1 || corners.size == 4) {
                    canvas.drawLine(a.x, a.y, b.x, b.y, quadPaint)
                }
            }
        }
        for (p in corners) {
            val v = toView(p)
            canvas.drawCircle(v.x, v.y, dp(9f), cornerPaint)
            canvas.drawCircle(v.x, v.y, dp(9f), cornerRing)
        }

        // Mätpunkterna
        if (measures.size == 2) {
            val a = toView(measures[0])
            val b = toView(measures[1])
            canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
        }
        for (p in measures) {
            val v = toView(p)
            canvas.drawCircle(v.x, v.y, dp(8f), measurePaint)
            canvas.drawCircle(v.x, v.y, dp(8f), cornerRing)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        val img = toImage(event.x, event.y) ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = nearestPoint(img)
                if (dragging == null) {
                    val p = PointF(img.x, img.y)
                    when {
                        corners.size < 4 -> corners.add(p)
                        measures.size < 2 -> measures.add(p)
                        else -> return true
                    }
                    dragging = p
                    notifyChange()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragging?.let {
                    it.x = img.x
                    it.y = img.y
                    invalidate()
                    notifyChange()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Närmsta befintliga punkt inom greppavstånd, annars null. */
    private fun nearestPoint(img: PointF): PointF? {
        val grabPx = dp(22f)
        var best: PointF? = null
        var bestDist = Float.MAX_VALUE
        for (p in corners + measures) {
            val v = toView(p)
            val vTouch = toView(img)
            val d = hypot(v.x - vTouch.x, v.y - vTouch.y)
            if (d < grabPx && d < bestDist) { best = p; bestDist = d }
        }
        return best
    }

    private fun toView(p: PointF): PointF {
        val arr = floatArrayOf(p.x, p.y)
        matrix.mapPoints(arr)
        return PointF(arr[0], arr[1])
    }

    private fun toImage(x: Float, y: Float): PointF? {
        val bm = bitmap ?: return null
        val arr = floatArrayOf(x, y)
        inverse.mapPoints(arr)
        return PointF(
            arr[0].coerceIn(0f, bm.width.toFloat()),
            arr[1].coerceIn(0f, bm.height.toFloat())
        )
    }

    private fun notifyChange() {
        listener?.onPointsChanged(corners.size, measures.size)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
