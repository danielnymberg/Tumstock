package se.nymberg.matverktyg

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Hittar ett bankkort (ISO ID-1, aspekt 1,586) i en bild och returnerar dess
 * fyra hörn i bitmap-koordinater — eller null, då det manuella flödet tar vid.
 *
 * Pipeline: nedskala → gråskala/blur → två kantkartor (Canny och adaptiv
 * tröskel) → konturer → konvexa fyrhörningar → poängsätt mot kortets
 * geometri → subpixel-förfining. Allt lokalt, defensivt (fel ⇒ null).
 */
object OpenCvCardDetector {

    @Volatile
    private var loaded: Boolean? = null

    /** Laddar native-biblioteket en gång; false om init misslyckas. */
    fun ensureLoaded(): Boolean {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val ok = try {
                OpenCVLoader.initLocal()
            } catch (t: Throwable) {
                false
            }
            loaded = ok
            return ok
        }
    }

    /** Körs på bakgrundstråd. Returnerar hörn TL→TR→BR→BL eller null. */
    fun detectCard(bitmap: Bitmap): List<PointF>? {
        if (!ensureLoaded()) return null
        return try {
            detect(bitmap)
        } catch (t: Throwable) {
            null
        }
    }

    private fun detect(bitmap: Bitmap): List<PointF>? {
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
                  else bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val rgba = Mat()
        Utils.bitmapToMat(src, rgba)
        val grayFull = Mat()
        Imgproc.cvtColor(rgba, grayFull, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()

        // Nedskala så max-dimension ≈ 1000 px.
        val maxDim = max(grayFull.cols(), grayFull.rows())
        val f = if (maxDim > 1000) 1000.0 / maxDim else 1.0
        val small = Mat()
        if (f < 1.0) {
            Imgproc.resize(grayFull, small, Size(grayFull.cols() * f, grayFull.rows() * f))
        } else {
            grayFull.copyTo(small)
        }

        val blurred = Mat()
        Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)

        // Kantkarta 1: Canny + dilate (sluter glapp vid rundade hörn).
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)

        // Kantkarta 2: adaptiv tröskel (klarar låg kontrast mot bordet).
        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            blurred, thresh, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 31, 7.0
        )

        val contours = ArrayList<MatOfPoint>()
        for (map in listOf(edges, thresh)) {
            val found = ArrayList<MatOfPoint>()
            Imgproc.findContours(
                map, found, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
            )
            contours.addAll(found)
        }

        val imgArea = small.cols().toDouble() * small.rows().toDouble()
        var best: List<PointF>? = null
        var bestScore = 0.55  // tröskel: under detta hellre manuellt än fel

        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < 0.01 * imgArea || area > 0.60 * imgArea) continue

            val curve = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(curve, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true)
            if (approx.total() != 4L) continue
            val approxPts = approx.toArray()
            if (!Imgproc.isContourConvex(MatOfPoint(*approxPts))) continue

            val quad = Homography.sortCorners(approxPts.map { PointF(it.x.toFloat(), it.y.toFloat()) })
            val score = scoreQuad(quad, area, imgArea)
            if (score > bestScore) {
                bestScore = score
                best = quad
            }
        }

        val winner = best ?: run {
            small.release(); blurred.release(); edges.release(); thresh.release(); grayFull.release()
            return null
        }

        // Tillbaka till fullbilds-koordinater + subpixel-förfining (max 4 px flytt).
        val fullPts = winner.map { PointF((it.x / f).toFloat(), (it.y / f).toFloat()) }
        val refined = try {
            val mat = MatOfPoint2f(*fullPts.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
            Imgproc.cornerSubPix(
                grayFull, mat, Size(11.0, 11.0), Size(-1.0, -1.0),
                TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.01)
            )
            mat.toArray().mapIndexed { i, p ->
                val orig = fullPts[i]
                val moved = hypot(p.x - orig.x, p.y - orig.y)
                if (moved <= 4.0) PointF(p.x.toFloat(), p.y.toFloat()) else orig
            }
        } catch (t: Throwable) {
            fullPts
        }

        small.release(); blurred.release(); edges.release(); thresh.release(); grayFull.release()
        return refined
    }

    /** Poäng 0–1 för hur kort-lik fyrhörningen är. */
    private fun scoreQuad(q: List<PointF>, area: Double, imgArea: Double): Double {
        val top = dist(q[0], q[1])
        val right = dist(q[1], q[2])
        val bottom = dist(q[2], q[3])
        val left = dist(q[3], q[0])
        val long = max((top + bottom) / 2, (right + left) / 2)
        val short = min((top + bottom) / 2, (right + left) / 2)
        if (short < 1e-3) return 0.0

        // Aspekt mot 1,586 — bred tolerans eftersom perspektiv skevar kvoten.
        val aspect = long / short
        if (aspect < 1.30 || aspect > 1.95) return 0.0
        val aspectScore = 1.0 - min(1.0, abs(aspect - 1.586) / 0.35)

        // Motstående sidor ungefär lika långa.
        val r1 = if (bottom > 1e-3) top / bottom else 0.0
        val r2 = if (left > 1e-3) right / left else 0.0
        val parallelScore =
            (if (r1 in 0.8..1.25) 1.0 else 0.0) * 0.5 +
            (if (r2 in 0.8..1.25) 1.0 else 0.0) * 0.5

        // Hörnvinklar nära 90°.
        var angleOk = 0
        for (i in 0 until 4) {
            val a = angleAt(q[(i + 3) % 4], q[i], q[(i + 1) % 4])
            if (abs(a - 90.0) <= 25.0) angleOk++
        }
        val angleScore = angleOk / 4.0

        // Rektangularitet: kvadareal mot omslutande parallellogramyta (shoelace).
        val quadArea = shoelace(q)
        val rectScore = if (quadArea > 1e-3) min(1.0, area / quadArea) else 0.0

        // Storleksbonus — kortet är sällan minsta fyrhörningen i bilden.
        val sizeScore = min(1.0, (area / imgArea) / 0.15)

        return 0.35 * aspectScore + 0.20 * angleScore + 0.20 * rectScore +
               0.15 * parallelScore + 0.10 * sizeScore
    }

    private fun angleAt(prev: PointF, at: PointF, next: PointF): Double {
        val v1x = prev.x - at.x; val v1y = prev.y - at.y
        val v2x = next.x - at.x; val v2y = next.y - at.y
        val n1 = hypot(v1x.toDouble(), v1y.toDouble())
        val n2 = hypot(v2x.toDouble(), v2y.toDouble())
        if (n1 < 1e-6 || n2 < 1e-6) return 0.0
        val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    private fun shoelace(q: List<PointF>): Double {
        var s = 0.0
        for (i in q.indices) {
            val a = q[i]
            val b = q[(i + 1) % q.size]
            s += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return abs(s) / 2.0
    }

    private fun dist(a: PointF, b: PointF): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}
