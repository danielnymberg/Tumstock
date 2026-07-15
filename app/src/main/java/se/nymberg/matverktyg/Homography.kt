package se.nymberg.matverktyg

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Plan homografi (projektiv avbildning) från fyra punktpar, via DLT och
 * Gauss-eliminering. Används för att mappa bildpixlar → millimeter i kortets
 * plan, vilket korrigerar både skala och perspektiv.
 */
object Homography {

    /** ISO/IEC 7810 ID-1 (bankkort) i millimeter. */
    const val CARD_LONG_MM = 85.60
    const val CARD_SHORT_MM = 53.98

    /**
     * Beräknar H så att dst ≈ H·src (homogena koordinater, h33 = 1).
     * Returnerar 9 element radvis, eller null om systemet är singulärt
     * (t.ex. tre hörn på linje).
     */
    fun compute(src: List<PointF>, dst: List<PointF>): DoubleArray? {
        require(src.size == 4 && dst.size == 4)
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)
        for (i in 0 until 4) {
            val x = src[i].x.toDouble()
            val y = src[i].y.toDouble()
            val u = dst[i].x.toDouble()
            val v = dst[i].y.toDouble()
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u)
            b[2 * i] = u
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v)
            b[2 * i + 1] = v
        }
        val h = solve(a, b) ?: return null
        return doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
    }

    /** Avbildar en punkt genom H. */
    fun map(h: DoubleArray, p: PointF): PointF {
        val w = h[6] * p.x + h[7] * p.y + h[8]
        if (abs(w) < 1e-12) return PointF(0f, 0f)
        val u = (h[0] * p.x + h[1] * p.y + h[2]) / w
        val v = (h[3] * p.x + h[4] * p.y + h[5]) / w
        return PointF(u.toFloat(), v.toFloat())
    }

    /** Avstånd i destinationsplanet (mm) mellan två bildpunkter. */
    fun distanceMm(h: DoubleArray, p1: PointF, p2: PointF): Double {
        val a = map(h, p1)
        val b = map(h, p2)
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
    }

    /**
     * Sorterar fyra godtyckligt tappade hörn till konsekvent ordning:
     * övre vänstra → övre högra → nedre högra → nedre vänstra (medurs i
     * bildkoordinater där y växer nedåt).
     */
    fun sortCorners(pts: List<PointF>): List<PointF> {
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()
        val sorted = pts.sortedBy { atan2((it.y - cy), (it.x - cx).toDouble()) }
        // Rotera så att första punkten är den övre vänstra (minst x+y).
        var startIdx = 0
        var best = Double.MAX_VALUE
        for (i in sorted.indices) {
            val s = sorted[i].x.toDouble() + sorted[i].y.toDouble()
            if (s < best) { best = s; startIdx = i }
        }
        return List(4) { sorted[(startIdx + it) % 4] }
    }

    /**
     * Bygger homografi bild→mm ur fyra kort-hörn (godtycklig ordning).
     * Avgör själv om kortet ligger liggande eller stående i bilden.
     */
    fun fromCardCorners(corners: List<PointF>): DoubleArray? {
        val c = sortCorners(corners)
        val top = dist(c[0], c[1])
        val side = dist(c[1], c[2])
        val (w, h) = if (top >= side) CARD_LONG_MM to CARD_SHORT_MM
                     else CARD_SHORT_MM to CARD_LONG_MM
        val dst = listOf(
            PointF(0f, 0f),
            PointF(w.toFloat(), 0f),
            PointF(w.toFloat(), h.toFloat()),
            PointF(0f, h.toFloat())
        )
        return compute(c, dst)
    }

    private fun dist(a: PointF, b: PointF): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /**
     * 95 %-osäkerhet (mm) för avståndet p1–p2, via Monte-Carlo: korthörnen
     * störs N(0, sigmaPx), homografin räknas om och spridningen mäts.
     * Deterministiskt frö → stabil siffra för samma markering.
     */
    fun estimateErrorMm(
        corners: List<PointF>,
        p1: PointF,
        p2: PointF,
        sigmaPx: Float = 2f,
        samples: Int = 24
    ): Double {
        val rnd = java.util.Random(42)
        val dists = ArrayList<Double>(samples)
        for (i in 0 until samples) {
            val jittered = corners.map {
                PointF(
                    it.x + (rnd.nextGaussian() * sigmaPx).toFloat(),
                    it.y + (rnd.nextGaussian() * sigmaPx).toFloat()
                )
            }
            val h = fromCardCorners(jittered) ?: continue
            dists.add(distanceMm(h, p1, p2))
        }
        if (dists.size < 4) return 0.0
        val mean = dists.average()
        val variance = dists.sumOf { (it - mean) * (it - mean) } / (dists.size - 1)
        return 2.0 * kotlin.math.sqrt(variance)
    }

    /** Gauss-eliminering med partiell pivotering. */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            }
            if (abs(a[pivot][col]) < 1e-10) return null
            if (pivot != col) {
                val tmp = a[pivot]; a[pivot] = a[col]; a[col] = tmp
                val t = b[pivot]; b[pivot] = b[col]; b[col] = t
            }
            for (r in col + 1 until n) {
                val f = a[r][col] / a[col][col]
                for (k in col until n) a[r][k] -= f * a[col][k]
                b[r] -= f * b[col]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = b[r]
            for (k in r + 1 until n) s -= a[r][k] * x[k]
            x[r] = s / a[r][r]
        }
        return x
    }
}
