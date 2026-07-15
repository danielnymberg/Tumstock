package se.nymberg.matverktyg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import se.nymberg.matverktyg.databinding.ActivityMainBinding
import java.io.File

/**
 * Foto-fliken: mät verkliga föremål i en bild med ett bankkort som referens.
 * Kortets fyra hörn ger en homografi (skala + perspektivkorrigering); två
 * mätpunkter ger avståndet i mm. Allt sker lokalt — bilden lämnar aldrig
 * enheten, och ingen CAMERA-behörighet behövs (systemkameran tar bilden).
 */
class PhotoController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) : PhotoMarkView.Listener {

    private var cameraUri: Uri? = null
    private var homography: DoubleArray? = null

    private val takePicture =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) cameraUri?.let { loadImage(it) }
        }

    private val pickImage =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { loadImage(it) }
        }

    fun init() {
        binding.photoView.listener = this

        binding.takePhotoButton.setOnClickListener {
            val dir = File(activity.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "capture.jpg")
            val uri = FileProvider.getUriForFile(
                activity, activity.packageName + ".fileprovider", file
            )
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.photo_load_error, Toast.LENGTH_SHORT).show()
            }
        }

        binding.pickPhotoButton.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.photoUndoButton.setOnClickListener {
            binding.photoView.undoLast()
        }
    }

    override fun onPointsChanged(corners: Int, measures: Int) {
        when {
            !binding.photoView.hasImage() -> {
                binding.photoHint.setText(R.string.photo_empty_hint)
                binding.photoResult.text = ""
            }
            corners < 4 -> {
                binding.photoHint.text =
                    activity.getString(R.string.photo_mark_corners, corners)
                binding.photoResult.text = ""
                homography = null
            }
            else -> {
                // Räkna om homografin varje gång (hörn kan ha finjusterats).
                homography = Homography.fromCardCorners(binding.photoView.corners)
                binding.photoHint.setText(R.string.photo_mark_points)
                if (measures == 2 && homography != null) {
                    val mm = Homography.distanceMm(
                        homography!!,
                        binding.photoView.measures[0],
                        binding.photoView.measures[1]
                    )
                    binding.photoResult.text =
                        activity.getString(R.string.photo_distance, mm, mm / 10.0)
                } else {
                    binding.photoResult.text = ""
                }
            }
        }
    }

    private fun loadImage(uri: Uri) {
        val bm = decodeScaled(uri, 2048)
        if (bm == null) {
            Toast.makeText(activity, R.string.photo_load_error, Toast.LENGTH_SHORT).show()
            return
        }
        binding.photoView.setImage(bm)
        binding.photoHint.text = activity.getString(R.string.photo_mark_corners, 0)
        binding.photoResult.text = ""
    }

    /** Läser bilden nedskalad (minne) och EXIF-roterad (kamerabilder). */
    private fun decodeScaled(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val resolver = activity.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotation == 0f) raw
            else {
                val m = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            }
        } catch (e: Exception) {
            null
        }
    }
}
