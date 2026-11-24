package ar.param.advisor.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Voz (TTS del sistema) y campana (spec del motor, REQ-030..REQ-037).
 * La campana se sintetiza en código: no depende de ningún audio de terceros (Q-04).
 */
class SpeechAndBellController(context: Context) : SpeechPort, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val bell = BellSynth()

    @Volatile private var speechRate = 1f
    @Volatile private var paceWpm = 120

    // Se asigna después de construirse; onInit no lo toca directamente (CON-003).
    @Volatile private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        mainHandler.post {
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Log.w(TAG, "El motor de voz no está disponible; la práctica sigue en silencio")
                ready.complete(false)
                return@post
            }
            configure(engine)
            ready.complete(true)
        }
    }

    private fun configure(engine: TextToSpeech) {
        val candidates = listOf(Locale.forLanguageTag("es-AR"), Locale.forLanguageTag("es-ES"), Locale.getDefault())
        val locale = candidates.firstOrNull {
            runCatching { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
        } ?: Locale.getDefault()
        engine.setLanguage(locale)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
    }

    private fun finish(utteranceId: String?) {
        utteranceId?.let { pending.remove(it)?.resume(Unit) }
    }

    override fun setPaceFromWpm(wpm: Int) {
        paceWpm = wpm.coerceAtLeast(40)
        speechRate = (wpm / 120f).coerceIn(0.72f, 1.25f)
    }

    override suspend fun speakAndAwait(text: String, mode: RecitationMode) {
        if (text.isBlank() || mode == RecitationMode.MENTAL) return
        val available = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { ready.await() } ?: false
        val engine = tts
        if (!available || engine == null) {
            // Sin voz: se respeta el tiempo de lectura para que la práctica no pase de golpe.
            delay(estimatedReadingMs(text))
            return
        }
        val whisper = mode == RecitationMode.WHISPER
        engine.setSpeechRate(speechRate)
        engine.setPitch(if (whisper) 0.86f else 1f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (whisper) 0.5f else 1f)
        }
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            pending[id] = cont
            cont.invokeOnCancellation {
                pending.remove(id)
                engine.stop()
            }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
                pending.remove(id)?.resume(Unit)
            }
        }
    }

    private fun estimatedReadingMs(text: String): Long {
        val words = text.trim().split(Regex("\\s+")).size
        return (words * 60_000L / paceWpm).coerceAtLeast(1_500L)
    }

    override fun ringBell(isCycleMilestone: Boolean) {
        bell.ring(if (isCycleMilestone) 0.6f else 0.8f)
    }

    override fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    override fun release() {
        pending.values.forEach { runCatching { it.resume(Unit) } }
        pending.clear()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        bell.release()
    }

    private companion object {
        const val TAG = "SpeechAndBell"
        const val TTS_INIT_TIMEOUT_MS = 5_000L
    }
}

/** Campana suave sintetizada (parciales de cuenco con caída exponencial). */
private class BellSynth {
    private val handler = Handler(Looper.getMainLooper())
    private val tracks = mutableSetOf<AudioTrack>()
    private val samples: ShortArray by lazy { synthesize() }

    fun ring(volume: Float) {
        try {
            val data = samples
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.setVolume(volume)
            track.play()
            synchronized(tracks) { tracks += track }
            handler.postDelayed({ releaseTrack(track) }, DURATION_MS + 300L)
        } catch (e: Exception) {
            Log.w("BellSynth", "No se pudo tocar la campana; se usa un tono de respaldo", e)
            fallbackTone()
        }
    }

    private fun fallbackTone() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 30)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            handler.postDelayed({ tone.release() }, 400L)
        }
    }

    private fun releaseTrack(track: AudioTrack) {
        synchronized(tracks) { tracks -= track }
        runCatching {
            track.stop()
            track.release()
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        synchronized(tracks) {
            tracks.forEach { runCatching { it.release() } }
            tracks.clear()
        }
    }

    private fun synthesize(): ShortArray {
        val n = (SAMPLE_RATE * DURATION_MS / 1000L).toInt()
        val partials = listOf(
            Triple(1.0, 1.0, 1.6),
            Triple(2.76, 0.45, 0.9),
            Triple(5.40, 0.18, 0.5),
        )
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.008).coerceAtMost(1.0)
            var v = 0.0
            for ((ratio, amp, decay) in partials) {
                v += amp * exp(-t / decay) * sin(2 * PI * FUNDAMENTAL_HZ * ratio * t)
            }
            out[i] = (attack * v * 0.42 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val DURATION_MS = 3_200L
        const val FUNDAMENTAL_HZ = 432.0
    }
}
