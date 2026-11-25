package ar.param.advisor.data.user

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Imágenes y grabaciones de las prácticas propias. Se copian a la carpeta privada de la app:
 * no dependen de la galería, no necesitan permisos de almacenamiento y nunca salen del teléfono.
 */
class UserMediaStore(context: Context) {
    private val appContext = context.applicationContext
    private val imagesDir get() = File(appContext.filesDir, "practice_images").apply { mkdirs() }
    private val recordingsDir get() = File(appContext.filesDir, "practice_recordings").apply { mkdirs() }

    /** Copia la imagen elegida, achicada a [MAX_IMAGE_SIDE] px para que no ocupe de más. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(uri) ?: return@runCatching null
            val out = File(imagesDir, "img_${UUID.randomUUID()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            out.absolutePath
        }.onFailure { Log.w(TAG, "No se pudo copiar la imagen", it) }.getOrNull()
    }

    /** Copia un archivo de audio elegido del teléfono (por ejemplo, un mantra grabado con otra app). */
    suspend fun importAudio(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(recordingsDir, "audio_${UUID.randomUUID()}")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            if (out.length() > MAX_AUDIO_BYTES) {
                out.delete()
                return@runCatching null
            }
            out.absolutePath
        }.onFailure { Log.w(TAG, "No se pudo copiar el audio", it) }.getOrNull()
    }

    fun newRecordingFile(): File = File(recordingsDir, "rec_${UUID.randomUUID()}.m4a")

    /** Borra un archivo propio que ya no se usa. Nunca toca nada fuera de las carpetas de la app. */
    fun deleteOwned(path: String?) {
        if (path == null) return
        val file = File(path)
        val owned = file.parentFile?.let { it == imagesDir || it == recordingsDir } == true
        if (owned) runCatching { file.delete() }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val scale = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private companion object {
        const val TAG = "UserMediaStore"
        const val MAX_IMAGE_SIDE = 1024
        const val MAX_AUDIO_BYTES = 30L * 1024 * 1024
    }
}
