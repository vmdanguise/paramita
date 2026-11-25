package ar.param.advisor.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Una voz instalada en el teléfono que funciona sin internet. */
data class VoiceOption(val name: String, val locale: Locale)

/**
 * Voz (TTS del sistema), grabaciones propias y campana (spec del motor, REQ-030..REQ-037).
 * La campana se sintetiza en código: no depende de ningún audio de terceros (Q-04).
 */
class SpeechAndBellController(
    context: Context,
    initialVoice: VoiceSettings = VoiceSettings(),
) : SpeechPort, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val bell = BellSynth()
    private val clipCache = object : LinkedHashMap<String, PcmClip>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PcmClip>?) = size > CLIP_CACHE_SIZE
    }

    @Volatile private var speechRate = 1f
    @Volatile private var paceWpm = 120
    @Volatile private var clipTrack: AudioTrack? = null
    @Volatile private var recordingPlayer: MediaPlayer? = null
    @Volatile private var defaultLocale: Locale = Locale.getDefault()
    @Volatile private var appliedVoiceName: String? = null

    /** Ajustes de voz vigentes; se pueden cambiar en cualquier momento (valen desde la próxima frase). */
    @Volatile var voice: VoiceSettings = initialVoice.normalized()
        set(value) {
            field = value.normalized()
        }

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
        defaultLocale = locale
        engine.setLanguage(locale)
        engine.setAudioAttributes(speechAttributes())
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
    }

    private fun speechAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun finish(utteranceId: String?) {
        utteranceId?.let { pending.remove(it)?.resume(Unit) }
    }

    /**
     * Voces en español instaladas en el teléfono. Se excluyen las que necesitan internet:
     * nada de lo que se recita sale del dispositivo.
     */
    suspend fun availableVoices(): List<VoiceOption> {
        val available = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { ready.await() } ?: false
        val engine = tts ?: return emptyList()
        if (!available) return emptyList()
        return runCatching {
            engine.voices.orEmpty()
                .filter { it.locale.language == "es" && !it.isNetworkConnectionRequired }
                .sortedWith(compareBy({ it.locale.country != defaultLocale.country }, { it.locale.toLanguageTag() }, { it.name }))
                .map { VoiceOption(it.name, it.locale) }
        }.getOrDefault(emptyList())
    }

    /** Aplica la voz elegida, o vuelve a la voz por defecto en español. */
    private fun applyVoice(engine: TextToSpeech) {
        val wanted = voice.voiceName
        if (wanted == appliedVoiceName) return
        runCatching {
            val match = wanted?.let { name -> engine.voices.orEmpty().firstOrNull { it.name == name } }
            if (match != null) engine.voice = match else engine.setLanguage(defaultLocale)
        }.onFailure { Log.w(TAG, "No se pudo cambiar la voz", it) }
        appliedVoiceName = wanted
    }

    /** 120 palabras por minuto es la velocidad normal; el control va de la mitad a 1,6 veces. */
    override fun setPaceFromWpm(wpm: Int) {
        paceWpm = wpm.coerceAtLeast(40)
        speechRate = (wpm / 120f).coerceIn(MIN_RATE, MAX_RATE)
    }

    override suspend fun speakAndAwait(text: String, mode: RecitationMode, audioPath: String?) {
        if (mode == RecitationMode.MENTAL) return
        val whisper = mode == RecitationMode.WHISPER
        val recording = audioPath?.let(::File)?.takeIf { it.isFile && it.length() > 0 }
        if (recording != null && playRecording(recording, whisper)) return
        if (text.isBlank()) return
        val available = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { ready.await() } ?: false
        val engine = tts
        if (!available || engine == null) {
            // Sin voz: se respeta el tiempo de lectura para que la práctica no pase de golpe.
            delay(estimatedReadingMs(text))
            return
        }
        applyVoice(engine)
        val v = voice
        if (whisper || !v.isFlatEq) {
            val clip = processedClip(engine, text, whisper, v)
            if (clip != null) {
                playClip(clip, v.volume)
                return
            }
        }
        engine.setSpeechRate(speechRate)
        engine.setPitch(v.pitch * if (whisper) 0.86f else 1f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, v.volume * if (whisper) 0.5f else 1f)
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
     * Grabación propia (mantra grabado por el usuario). La velocidad se aplica sin cambiar
     * el tono. Devuelve false si no se pudo reproducir, y entonces se usa la voz sintética.
     */
    private suspend fun playRecording(file: File, whisper: Boolean): Boolean {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(speechAttributes())
            withContext(Dispatchers.IO) {
                player.setDataSource(file.absolutePath)
                player.prepare()
            }
            val volume = voice.volume * if (whisper) 0.5f else 1f
            player.setVolume(volume, volume)
            recordingPlayer = player
            suspendCancellableCoroutine { cont ->
                player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                player.setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                cont.invokeOnCancellation { runCatching { player.stop() } }
                if (abs(speechRate - 1f) > 0.01f) {
                    runCatching { player.playbackParams = player.playbackParams.setSpeed(speechRate) }
                }
                if (!player.isPlaying) player.start()
            }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "No se pudo reproducir la grabación; se usa la voz sintética", e)
            false
        } finally {
            recordingPlayer = null
            runCatching { player.release() }
        }
    }

    /**
     * El TTS genera la frase en un archivo; si es susurro se le quita la voz sonora ([Whisperizer])
     * y después se ecualiza ([VoiceEqualizer]). Se guarda en memoria porque los mantras se repiten.
     * Si algo falla devuelve null y se usa la voz directa del motor.
     */
    private suspend fun processedClip(engine: TextToSpeech, text: String, whisper: Boolean, v: VoiceSettings): PcmClip? {
        val rate = if (whisper) speechRate * WHISPER_RATE_FACTOR else speechRate
        val key = "$whisper|$rate|${v.cacheKey}|$text"
        synchronized(clipCache) { clipCache[key] }?.let { return it }
        val file = File(appContext.cacheDir, "voz_${UUID.randomUUID()}.wav")
        return try {
            engine.setSpeechRate(rate)
            engine.setPitch(v.pitch)
            val id = UUID.randomUUID().toString()
            val ok = withTimeoutOrNull(SYNTH_TIMEOUT_MS) {
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
                    ?.let { raw ->
                        val base = if (whisper) Whisperizer.process(raw.samples, raw.sampleRate) else raw.samples
                        val eq = if (v.isFlatEq) base else VoiceEqualizer.process(base, raw.sampleRate, v.bassDb, v.midDb, v.trebleDb)
                        PcmClip(eq, raw.sampleRate)
                    }
            } ?: return null
            synchronized(clipCache) { clipCache[key] = clip }
            clip
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "No se pudo preparar la voz procesada; se usa la voz directa", e)
            null
        } finally {
            file.delete()
        }
    }

    private suspend fun playClip(clip: PcmClip, volume: Float) {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(speechAttributes())
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
            Log.w(TAG, "No se pudo reproducir la voz procesada", e)
            delay(clip.samples.size * 1000L / clip.sampleRate)
            return
        }
        try {
            track.write(clip.samples, 0, clip.samples.size)
            track.setVolume(volume)
            clipTrack = track
            track.play()
            delay(clip.samples.size * 1000L / clip.sampleRate + 60L)
        } finally {
            clipTrack = null
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
        runCatching { clipTrack?.pause() }
        runCatching { recordingPlayer?.pause() }
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
        const val SYNTH_TIMEOUT_MS = 15_000L
        const val WHISPER_RATE_FACTOR = 0.9f
        const val CLIP_CACHE_SIZE = 24
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 1.6f
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
