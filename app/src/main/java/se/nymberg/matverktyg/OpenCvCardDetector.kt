package se.nymberg.matverktyg

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
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
 * Primär väg: nyans-binnad färgsegmentering (kort är oftast enfärgat och
 * mättat; separerar kortet från mönstrade underlag som annars växer ihop med
 * konturen). Sekundärt: gråskalekartor (Canny/adaptiv/Otsu) med eps-svep i
 * approxPolyDP och minAreaRect-fallback — båda tål RUNDADE hörn, eftersom
 * minAreaRect ger det omslutande "skarpa" hörnet.
 *
 * Verifierad mot verkligt foto (blått kort på jordgubbsmönstrad duk) där
 * enbart gråskalevägen misslyckades. Allt lokalt, defensivt (fel ⇒ null).
 */
object OpenCvCardDetector {

    private const val CARD_ASPECT = 85.60 / 53.98

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

    /**
     * Magnetiska kanter för MANUELL markering: tar fyra ungefärliga hörn,
     * letar upp starkaste raka kanten nära varje sida (gradientsök längs
     * normalerna + minsta-kvadrat-linje) och returnerar linjekorsningarna
     * som exakta hörn. Rundade hörn spelar ingen roll — sidorna avgör.
     * Flytten begränsas till [maxShiftPx]. Null om något går fel.
     */
    fun refineQuad(bitmap: Bitmap, rough: List<PointF>, maxShiftPx: Float = 30f): List<PointF>? {
        if (!ensureLoaded() || rough.size != 4) return null
        return try {
            val src = ensureArgb(bitmap)
            val rgba = Mat()
            Utils.bitmapToMat(src, rgba)
            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            rgba.release()
            val sorted = Homography.sortCorners(rough)
            val refined = snapToEdges(gray, sorted, maxShiftPx)
            gray.release()
            refined
        } catch (t: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------------

    private fun ensureArgb(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)

    private fun detect(bitmap: Bitmap): List<PointF>? {
        val src = ensureArgb(bitmap)
        val rgba = Mat()
        Utils.bitmapToMat(src, rgba)

        // Nedskala så max-dimension ≈ 1000 px.
        val maxDim = max(rgba.cols(), rgba.rows())
        val f = if (maxDim > 1000) 1000.0 / maxDim else 1.0
        val small = Mat()
        if (f < 1.0) Imgproc.resize(rgba, small, Size(rgba.cols() * f, rgba.rows() * f))
        else rgba.copyTo(small)

        val grayFull = Mat()
        Imgproc.cvtColor(rgba, grayFull, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
        val imgArea = small.cols().toDouble() * small.rows().toDouble()

        var best: List<PointF>? = null
        var bestScore = 0.55

        fun consider(quad: List<PointF>, area: Double, colorBonus: Double) {
            val s = min(1.0, scoreQuad(quad, area, imgArea) + colorBonus)
            if (s > bestScore) {
                bestScore = s
                best = quad
            }
        }

        // --- Väg 1: nyans-binnad färgsegmentering (12 band à 15 hue-enheter) ---
        val rgb = Mat()
        Imgproc.cvtColor(small, rgb, Imgproc.COLOR_RGBA2RGB)
        val hsv = Mat()
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
        rgb.release()
        val kOpen = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        val kClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(13.0, 13.0))
        for (b in 0 until 12) {
            val lo = b * 15.0
            val mask = Mat()
            Core.inRange(hsv, Scalar(lo, 60.0, 60.0), Scalar(lo + 15.0, 255.0, 255.0), mask)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kOpen)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kClose)
            val cnts = ArrayList<MatOfPoint>()
            Imgproc.findContours(mask, cnts, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            mask.release()
            for (c in cnts) {
                val a = Imgproc.contourArea(c)
                if (a < 0.01 * imgArea || a > 0.60 * imgArea) continue
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*c.toArray()))
                val rw = rect.size.width
                val rh = rect.size.height
                if (rw < 1 || rh < 1) continue
                val solidity = a / (rw * rh)
                val asp = max(rw, rh) / min(rw, rh)
                if (solidity >= 0.85 && asp in 1.30..1.95) {
                    // Enfärgad, solid, kort-formad region = stark evidens.
                    consider(boxToQuad(rect), a, colorBonus = 0.10)
                }
            }
        }
        hsv.release()

        // --- Väg 2: gråskalekartor med eps-svep + minAreaRect-fallback ---
        val edges = Mat()
        Imgproc.Canny(blur, edges, 50.0, 150.0)
        val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, k3)
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)))
        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            blur, thresh, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 31, 7.0
        )
        val otsu = Mat()
        Imgproc.threshold(blur, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        for (map in listOf(edges, thresh, otsu)) {
            val cnts = ArrayList<MatOfPoint>()
            Imgproc.findContours(map, cnts, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            for (c in cnts) {
                val a = Imgproc.contourArea(c)
                if (a < 0.01 * imgArea || a > 0.60 * imgArea) continue
                val curve = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(curve, true)
                var quad: List<PointF>? = null
                // Eps-svep: rundade hörn kräver större eps för att kollapsa till 4 punkter.
                for (eps in doubleArrayOf(0.02, 0.035, 0.05)) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(curve, approx, eps * peri, true)
                    if (approx.total() == 4L) {
                        val pts = approx.toArray()
                        if (Imgproc.isContourConvex(MatOfPoint(*pts))) {
                            quad = Homography.sortCorners(pts.map { PointF(it.x.toFloat(), it.y.toFloat()) })
                            break
                        }
                    }
                }
                if (quad == null) {
                    // Fallback: solid kontur ≈ roterad rektangel (skarpa extrapolerade hörn).
                    val rect = Imgproc.minAreaRect(curve)
                    val rw = rect.size.width
                    val rh = rect.size.height
                    if (rw >= 1 && rh >= 1 && a / (rw * rh) >= 0.88) {
                        quad = boxToQuad(rect)
                    }
                }
                quad?.let { consider(it, a, colorBonus = 0.0) }
            }
        }
        edges.release(); thresh.release(); otsu.release(); blur.release(); gray.release(); small.release()

        val winner = best ?: run { grayFull.release(); return null }

        // Fullbilds-koordinater + kantsnäppning (ersätter cornerSubPix, som
        // drar hörnen mot den rundade kanten i stället för det skarpa hörnet).
        val fullPts = winner.map { PointF((it.x / f).toFloat(), (it.y / f).toFloat()) }
        val refined = snapToEdges(grayFull, Homography.sortCorners(fullPts), maxShiftPx = 12f)
            ?: fullPts
        grayFull.release()
        return refined
    }

    private fun boxToQuad(rect: org.opencv.core.RotatedRect): List<PointF> {
        val pts = Array(4) { Point() }
        rect.points(pts)
        return Homography.sortCorners(pts.map { PointF(it.x.toFloat(), it.y.toFloat()) })
    }

    /**
     * Kantsnäppning: för varje sida samplas 20 punkter längs mittre 60 % av
     * segmentet (bort från rundade hörn); vid varje punkt söks max-gradienten
     * ±14 px längs normalen; en linje anpassas (minsta kvadrat) och hörnen
     * blir linjekorsningar. Flytt per hörn begränsas till [maxShiftPx].
     */
    private fun snapToEdges(gray: Mat, quad: List<PointF>, maxShiftPx: Float): List<PointF>? {
        val lines = ArrayList<DoubleArray>(4) // (px, py, dx, dy)
        for (i in 0 until 4) {
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val len = hypot(dx, dy)
            if (len < 20) return null
            val ux = dx / len
            val uy = dy / len
            val nx = -uy
            val ny = ux
            val xs = ArrayList<Double>()
            val ys = ArrayList<Double>()
            var t = 0.20
            while (t <= 0.801) {
                val px = a.x + dx * t
                val py = a.y + dy * t
                var bestOff = 0.0
                var bestG = -1.0
                var off = -14.0
                while (off <= 14.0) {
                    val x1 = px + nx * (off + 1.5)
                    val y1 = py + ny * (off + 1.5)
                    val x2 = px + nx * (off - 1.5)
                    val y2 = py + ny * (off - 1.5)
                    val g = abs(pixel(gray, x1, y1) - pixel(gray, x2, y2))
                    if (g > bestG) { bestG = g; bestOff = off }
                    off += 1.0
                }
                if (bestG >= 8.0) { // kräve en riktig kant, inte brus
                    xs.add(px + nx * bestOff)
                    ys.add(py + ny * bestOff)
                }
                t += 0.60 / 19.0
            }
            if (xs.size < 8) return null
            lines.add(fitLine(xs, ys))
        }
        val out = ArrayList<PointF>(4)
        for (i in 0 until 4) {
            val p = intersect(lines[(i + 3) % 4], lines[i]) ?: return null
            val orig = quad[i]
            val moved = hypot((p.x - orig.x).toDouble(), (p.y - orig.y).toDouble())
            out.add(if (moved <= maxShiftPx) p else orig)
        }
        return out
    }

    private fun pixel(gray: Mat, x: Double, y: Double): Double {
        val xi = x.toInt()
        val yi = y.toInt()
        if (xi < 0 || yi < 0 || xi >= gray.cols() || yi >= gray.rows()) return 0.0
        val v = gray.get(yi, xi) ?: return 0.0
        return v[0]
    }

    /** Total minsta kvadrat via PCA — klarar lodräta linjer. Ger (px,py,dx,dy). */
    private fun fitLine(xs: List<Double>, ys: List<Double>): DoubleArray {
        val mx = xs.average()
        val my = ys.average()
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            sxx += dx * dx; sxy += dx * dy; syy += dy * dy
        }
        // Huvudegenvektor för 2x2-kovariansen.
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val l1 = tr / 2 + Math.sqrt(max(0.0, tr * tr / 4 - det))
        var dx = sxy
        var dy = l1 - sxx
        if (abs(dx) < 1e-9 && abs(dy) < 1e-9) { dx = l1 - syy; dy = sxy }
        val n = hypot(dx, dy)
        if (n < 1e-9) return doubleArrayOf(mx, my, 1.0, 0.0)
        return doubleArrayOf(mx, my, dx / n, dy / n)
    }

    private fun intersect(l1: DoubleArray, l2: DoubleArray): PointF? {
        val x1 = l1[0]; val y1 = l1[1]; val d1x = l1[2]; val d1y = l1[3]
        val x2 = l2[0]; val y2 = l2[1]; val d2x = l2[2]; val d2y = l2[3]
        val denom = d1x * d2y - d1y * d2x
        if (abs(denom) < 1e-9) return null
        val t = ((x2 - x1) * d2y - (y2 - y1) * d2x) / denom
        return PointF((x1 + d1x * t).toFloat(), (y1 + d1y * t).toFloat())
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

        val aspect = long / short
        if (aspect < 1.30 || aspect > 1.95) return 0.0
        val aspectScore = 1.0 - min(1.0, abs(aspect - CARD_ASPECT) / 0.35)

        val r1 = if (bottom > 1e-3) top / bottom else 0.0
        val r2 = if (left > 1e-3) right / left else 0.0
        val parallelScore =
            (if (r1 in 0.8..1.25) 1.0 else 0.0) * 0.5 +
            (if (r2 in 0.8..1.25) 1.0 else 0.0) * 0.5

        var angleOk = 0
        for (i in 0 until 4) {
            val a = angleAt(q[(i + 3) % 4], q[i], q[(i + 1) % 4])
            if (abs(a - 90.0) <= 25.0) angleOk++
        }
        val angleScore = angleOk / 4.0

        val quadArea = shoelace(q)
        val rectScore = if (quadArea > 1e-3) min(1.0, area / quadArea) else 0.0

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
