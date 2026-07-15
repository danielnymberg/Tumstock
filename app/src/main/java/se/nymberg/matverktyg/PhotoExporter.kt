package se.nymberg.matverktyg

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mätfoto: ritar mätlinjer + värden på en kopia av bilden och sparar den i
 * Bilder/Mätverktyg via MediaStore (ingen behörighet behövs på API 29+),
 * eller delar den. Så minns man vad man mätte.
 */
object PhotoExporter {

    /** Kopierar bitmapen och ritar linjer, etikett-pills och en diskret footer. */
    fun render(
        src: Bitmap,
        lines: List<PhotoMarkView.MeasureLine>,
        values: List<String>
    ): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        // Skala ritningen efter bildens storlek så exporten ser ut som vyn.
        val s = out.width / 1080f

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1F1E")
            style = Paint.Style.STROKE
            strokeWidth = 5f * s
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1F1E")
            style = Paint.Style.FILL
        }
        val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2EFE8")
            style = Paint.Style.STROKE
            strokeWidth = 4f * s
        }
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2EFE8")
            style = Paint.Style.FILL
        }
        val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1F1E")
            style = Paint.Style.STROKE
            strokeWidth = 2f * s
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1F1E")
            textSize = 34f * s
            textAlign = Paint.Align.CENTER
        }

        for (i in lines.indices) {
            val a = lines[i].a
            val b = lines[i].b
            canvas.drawLine(a.x, a.y, b.x, b.y, line)
            for (p in listOf(a, b)) {
                canvas.drawCircle(p.x, p.y, 12f * s, dot)
                canvas.drawCircle(p.x, p.y, 12f * s, dotRing)
            }
            val label = ('A' + (i % 26)).toString() +
                (values.getOrNull(i)?.let { "  $it" } ?: "")
            val cx = (a.x + b.x) / 2f
            val cy = (a.y + b.y) / 2f
            val w = text.measureText(label)
            val rect = RectF(
                cx - w / 2 - 18f * s, cy - 30f * s,
                cx + w / 2 + 18f * s, cy + 24f * s
            )
            canvas.drawRoundRect(rect, 18f * s, 18f * s, pillBg)
            canvas.drawRoundRect(rect, 18f * s, 18f * s, pillStroke)
            canvas.drawText(label, cx, cy + 10f * s, text)
        }

        // Diskret footer
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AA1B1F1E")
            textSize = 24f * s
        }
        canvas.drawText("Mätverktyg · danyapps.se", 16f * s, out.height - 16f * s, footer)
        return out
    }

    /** Sparar till Bilder/Mätverktyg. Returnerar content-Uri eller null vid fel. */
    fun saveToPictures(activity: Activity, bm: Bitmap): Uri? {
        return try {
            val name = "matverktyg-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mätverktyg")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = activity.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bm.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (t: Throwable) {
            null
        }
    }

    fun share(activity: Activity, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, null))
    }
}
