package se.nymberg.matverktyg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Visar ett foto (fit-center) och låter användaren markera:
 *   1. bankkortets fyra hörn (terrakotta, streckad fyrhörning)
 *   2. upp till [MAX_LINES] mätlinjer (bläck) med etiketter A, B, C …
 *
 * Punkter dras med en förstoringslupp ([Loupe]) och lyfts ~48 dp ovanför
 * fingret under drag så att markören aldrig döljs av handen.
 * Alla punkter lagras i BILD-koordinater; vyn sköter transformationen.
 */
class PhotoMarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class MeasureLine(val a: PointF, val b: PointF)

    interface Listener {
        fun onPointsChanged(corners: Int, lines: Int, pending: Boolean)
    }

    var listener: Listener? = null

    /** Anropas när ett KORT-hörn släpps efter drag/placering (för kantsnäpp). */
    var onCornerReleased: (() -> Unit)? = null

    /** Formaterade värden per mätlinje (sätts av controllern), t.ex. "312 mm". */
    var lineValues: List<String> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val loupe = Loupe(resources.displayMetrics.density)

    val corners = ArrayList<PointF>(4)
    val lines = ArrayList<MeasureLine>()
    var pendingPoint: PointF? = null
        private set

    // Drag-tillstånd
    private var dragging: PointF? = null
    private var grabOffsetX = 0f
    private var grabOffsetY = 0f
    private var dragDistance = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B04A3A")
        style = Paint.Style.FILL
    }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2EFE8")
        style = Paint.Style.FILL
    }
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1F1E")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1F1E")
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
    }

    fun setImage(bm: Bitmap?) {
        bitmap = bm
        corners.clear()
        lines.clear()
        lineValues = emptyList()
        pendingPoint = null
        dragging = null
        loupe.active = false
        requestLayout()
        updateMatrix()
        invalidate()
        notifyChange()
    }

    fun hasImage(): Boolean = bitmap != null

    /** Kopia av bilden som exporten ritar på. */
    fun currentBitmap(): Bitmap? = bitmap

    /** Sätt auto-detekterade hörn (från OpenCV). */
    fun setDetectedCorners(pts: List<PointF>) {
        corners.clear()
        corners.addAll(pts.take(4).map { PointF(it.x, it.y) })
        invalidate()
        notifyChange()
    }

    fun undoLast() {
        when {
            pendingPoint != null -> pendingPoint = null
            lines.isNotEmpty() -> lines.removeAt(lines.size - 1)
            corners.isNotEmpty() -> corners.removeAt(corners.size - 1)
        }
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
            canvas.drawCircle(v.x, v.y, dp(9f), handleRing)
        }

        // Mätlinjer med etikett-pill vid mittpunkten
        for (i in lines.indices) {
            val a = toView(lines[i].a)
            val b = toView(lines[i].b)
            canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
            canvas.drawCircle(a.x, a.y, dp(7f), measurePaint)
            canvas.drawCircle(a.x, a.y, dp(7f), handleRing)
            canvas.drawCircle(b.x, b.y, dp(7f), measurePaint)
            canvas.drawCircle(b.x, b.y, dp(7f), handleRing)
            drawPill(canvas, (a.x + b.x) / 2f, (a.y + b.y) / 2f, labelFor(i))
        }

        // Pågående mätpunkt (första av två)
        pendingPoint?.let {
            val v = toView(it)
            canvas.drawCircle(v.x, v.y, dp(7f), measurePaint)
            canvas.drawCircle(v.x, v.y, dp(7f), handleRing)
        }

        // Lupp överst
        dragging?.let {
            loupe.draw(canvas, bm, matrix, toView(it), width, height)
        }
    }

    private fun labelFor(i: Int): String {
        val letter = ('A' + (i % 26)).toString()
        val value = lineValues.getOrNull(i)
        return if (value.isNullOrEmpty()) letter else "$letter  $value"
    }

    private fun drawPill(canvas: Canvas, cx: Float, cy: Float, text: String) {
        val padH = dp(8f)
        val padV = dp(5f)
        val w = pillText.measureText(text)
        val rect = RectF(cx - w / 2 - padH, cy - dp(9f) - padV, cx + w / 2 + padH, cy + dp(9f) + padV)
        val r = dp(9f)
        canvas.drawRoundRect(rect, r, r, pillBg)
        canvas.drawRoundRect(rect, r, r, pillStroke)
        canvas.drawText(text, cx, cy + dp(5f), pillText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bm = bitmap ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragDistance = 0f
                val grabbed = nearestPoint(event.x, event.y)
                if (grabbed != null) {
                    dragging = grabbed
                    val v = toView(grabbed)
                    grabOffsetX = v.x - event.x
                    grabOffsetY = v.y - event.y
                } else {
                    val img = toImage(event.x, event.y) ?: return true
                    val p = PointF(img.x, img.y)
                    when {
                        corners.size < 4 -> corners.add(p)
                        pendingPoint == null && lines.size < MAX_LINES -> pendingPoint = p
                        pendingPoint != null -> {
                            lines.add(MeasureLine(pendingPoint!!, p))
                            pendingPoint = null
                        }
                        else -> return true
                    }
                    dragging = p
                    grabOffsetX = 0f
                    grabOffsetY = 0f
                    notifyChange()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val d = dragging ?: return true
                dragDistance += hypot(event.x - lastTouchX, event.y - lastTouchY)
                lastTouchX = event.x
                lastTouchY = event.y
                // Lyft punkten mjukt till ~48 dp ovanför fingret så den syns.
                val lift = (dragDistance / dp(32f)).coerceIn(0f, 1f) * dp(48f)
                val img = toImage(event.x + grabOffsetX, event.y + grabOffsetY - lift)
                    ?: return true
                d.x = img.x
                d.y = img.y
                loupe.active = true
                invalidate()
                notifyChange()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasCorner = dragging != null && corners.contains(dragging)
                dragging = null
                loupe.active = false
                invalidate()
                if (wasCorner && corners.size == 4) onCornerReleased?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Närmsta befintliga punkt inom greppavstånd (vy-koordinater), annars null. */
    private fun nearestPoint(x: Float, y: Float): PointF? {
        val grabPx = dp(24f)
        var best: PointF? = null
        var bestDist = Float.MAX_VALUE
        val all = ArrayList<PointF>(corners)
        pendingPoint?.let { all.add(it) }
        for (l in lines) { all.add(l.a); all.add(l.b) }
        for (p in all) {
            val v = toView(p)
            val d = hypot(v.x - x, v.y - y)
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
        listener?.onPointsChanged(corners.size, lines.size, pendingPoint != null)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        const val MAX_LINES = 8
    }
}
