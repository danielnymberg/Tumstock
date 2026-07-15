package se.nymberg.matverktyg

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF

/**
 * Cirkulär förstoringslupp som ritas ovanför den punkt användaren drar,
 * med krysshår i mitten. Löser att markören annars döljs under fingret.
 * Ren rithjälpare — ingen egen View; anropas från [PhotoMarkView.onDraw].
 */
class Loupe(private val density: Float) {

    var active = false

    private val radius = 48f * density
    private val zoom = 2.5f
    private val offsetY = 110f * density
    private val edgePad = 8f * density

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#1B1F1E")
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f * density
        color = Color.parseColor("#B04A3A")
    }
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#ECE8DE")
    }
    private val clipPath = Path()
    private val loupeMatrix = Matrix()

    /**
     * @param imageMatrix bild→vy-matrisen som vyn ritar bitmapen med
     * @param pointView   den aktiva punkten i VY-koordinater
     */
    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        imageMatrix: Matrix,
        pointView: PointF,
        viewW: Int,
        viewH: Int
    ) {
        if (!active) return

        var cx = pointView.x
        var cy = pointView.y - offsetY
        // Kant-flip: hamnar luppen utanför övre kanten läggs den under punkten.
        if (cy < radius + edgePad) cy = pointView.y + offsetY
        cx = cx.coerceIn(radius + edgePad, viewW - radius - edgePad)
        cy = cy.coerceIn(radius + edgePad, viewH - radius - edgePad)

        canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.drawCircle(cx, cy, radius, backdrop)

        // Rita bitmapen förstorad så att pointView hamnar i luppens centrum.
        loupeMatrix.set(imageMatrix)
        loupeMatrix.postTranslate(-pointView.x, -pointView.y)
        loupeMatrix.postScale(zoom, zoom)
        loupeMatrix.postTranslate(cx, cy)
        canvas.drawBitmap(bitmap, loupeMatrix, null)
        canvas.restore()

        canvas.drawCircle(cx, cy, radius, ring)
        val ch = 10f * density
        canvas.drawLine(cx - ch, cy, cx + ch, cy, cross)
        canvas.drawLine(cx, cy - ch, cx, cy + ch, cross)
    }
}
