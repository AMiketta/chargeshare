package app.chargeshare.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.chargeshare.platform.PhotoPickResult
import app.chargeshare.platform.PhotoPicker
import app.chargeshare.platform.PickedPhoto
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Fotoauswahl über den Android Photo Picker (braucht keine Speicherberechtigung).
 *
 * Das Bild wird hier verkleinert (längste Seite höchstens [MAX_EDGE_PX]), nach den EXIF-Angaben gedreht und als JPEG
 * kodiert. Der Server entfernt EXIF/GPS ohnehin, wendet die Drehung aber nicht an; das übernimmt dieser Picker.
 *
 * Muss in `onCreate` der Activity erzeugt werden, weil der Result-Launcher vor dem Start registriert sein muss.
 */
class AndroidPhotoPicker(private val activity: ComponentActivity) : PhotoPicker {
    override val isSupported: Boolean = true

    private var pending: CancellableContinuation<Uri?>? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pending?.resume(uri)
        pending = null
    }

    override suspend fun pick(): PhotoPickResult {
        val uri = suspendCancellableCoroutine<Uri?> { continuation ->
            pending?.resume(null)
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } ?: return PhotoPickResult.Canceled
        return withContext(Dispatchers.IO) {
            val jpeg = runCatching { prepare(uri) }.getOrNull()
            if (jpeg == null) PhotoPickResult.Unreadable else PhotoPickResult.Picked(PickedPhoto(jpeg, "foto.jpg", "image/jpeg"))
        }
    }

    private fun prepare(uri: Uri): ByteArray? {
        val resolver = activity.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: IOException) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix().apply { applyOrientation(orientation) }
        val longest = maxOf(decoded.width, decoded.height)
        if (longest > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / longest
            matrix.postScale(scale, scale)
        }
        val result = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        val ok = result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (result !== decoded) decoded.recycle()
        result.recycle()
        return if (ok) out.toByteArray() else null
    }

    private fun Matrix.applyOrientation(orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }

    private companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 85
    }
}
