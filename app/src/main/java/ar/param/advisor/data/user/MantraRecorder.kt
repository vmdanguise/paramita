package ar.param.advisor.data.user

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** Graba un mantra con el micrófono (AAC en .m4a) y lo deja escuchar antes de guardarlo. */
class MantraRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(file: File): Boolean {
        stopPlayback()
        cancel()
        return try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo empezar a grabar", e)
            file.delete()
            false
        }
    }

    /** Termina la grabación y devuelve la ruta, o null si quedó vacía o falló. */
    fun stop(): String? {
        val r = recorder ?: return null
        val file = output
        recorder = null
        output = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || file == null || file.length() == 0L) {
            file?.delete()
            return null
        }
        return file.absolutePath
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        output?.delete()
        output = null
    }

    fun play(path: String, onDone: () -> Unit) {
        stopPlayback()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stopPlayback()
                    onDone()
                }
                prepare()
                start()
            }
        }.onFailure {
            Log.w(TAG, "No se pudo escuchar la grabación", it)
            onDone()
        }.getOrNull()
    }

    fun stopPlayback() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    fun release() {
        cancel()
        stopPlayback()
    }

    private companion object {
        const val TAG = "MantraRecorder"
    }
}
