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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
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
    private val whisperCache = object : LinkedHashMap<String, PcmClip>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PcmClip>?) = size > WHISPER_CACHE_SIZE
    }

    @Volatile private var speechRate = 1f
    @Volatile private var paceWpm = 120
    @Volatile private var whisperTrack: AudioTrack? = null

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
        if (mode == RecitationMode.WHISPER) {
            val clip = whisperClip(engine, text)
            if (clip != null) {
                playClip(clip)
                return
            }
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

    /**
     * Susurro real: el TTS genera la frase en un archivo, se le quita la voz sonora
     * ([Whisperizer]) y se guarda en memoria, porque los mantras se repiten muchas veces.
     * Si algo falla devuelve null y se usa la voz normal a volumen bajo.
     */
    private suspend fun whisperClip(engine: TextToSpeech, text: String): PcmClip? {
        val rate = speechRate * WHISPER_RATE_FACTOR
        val key = "$rate|$text"
        synchronized(whisperCache) { whisperCache[key] }?.let { return it }
        val file = File(appContext.cacheDir, "susurro_${UUID.randomUUID()}.wav")
        return try {
            engine.setSpeechRate(rate)
            engine.setPitch(1f)
            val id = UUID.randomUUID().toString()
            val ok = withTimeoutOrNull(WHISPER_SYNTH_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    pending[id] = cont
                    cont.invokeOnCancellation { pending.remove(id) }
                    if (engine.synthesizeToFile(text, Bundle(), file, id) == TextToSpeech.ERROR) {
                        pending.remove(id)?.resume(Unit)
                    }
                }
                true
            } ?: false
            if (!ok || !file.exists()) return null
            val clip = withContext(Dispatchers.Default) {
                WavPcm.parse(file.readBytes())
                    ?.takeIf { it.samples.isNotEmpty() }
                    ?.let { PcmClip(Whisperizer.process(it.samples, it.sampleRate), it.sampleRate) }
            } ?: return null
            synchronized(whisperCache) { whisperCache[key] = clip }
            clip
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "No se pudo preparar el susurro; se usa la voz suave", e)
            null
        } finally {
            file.delete()
        }
    }

    private suspend fun playClip(clip: PcmClip) {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(clip.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(clip.samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo reproducir el susurro", e)
            delay(clip.samples.size * 1000L / clip.sampleRate)
            return
        }
        try {
            track.write(clip.samples, 0, clip.samples.size)
            whisperTrack = track
            track.play()
            delay(clip.samples.size * 1000L / clip.sampleRate + 60L)
        } finally {
            whisperTrack = null
            runCatching {
                track.stop()
                track.release()
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
        runCatching { whisperTrack?.pause() }
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
        const val WHISPER_SYNTH_TIMEOUT_MS = 15_000L
        const val WHISPER_RATE_FACTOR = 0.9f
        const val WHISPER_CACHE_SIZE = 24
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
