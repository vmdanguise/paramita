package ar.param.advisor.data.settings

import android.content.Context
import androidx.core.content.edit
import ar.param.advisor.engine.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencias personales simples (se incluyen en el backup de Google, Q-08). */
class ManiSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _startWithRefuge = MutableStateFlow(prefs.getBoolean(KEY_START_WITH_REFUGE, true))
    /** Al abrir la app, recitar el refugio en susurro (pedido de Vicente, 2026-11-24). */
    val startWithRefuge: StateFlow<Boolean> = _startWithRefuge.asStateFlow()

    fun setStartWithRefuge(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_START_WITH_REFUGE, enabled) }
        _startWithRefuge.value = enabled
    }

    private val _startupPracticeId = MutableStateFlow(prefs.getString(KEY_STARTUP_PRACTICE, null) ?: REFUGE_PRACTICE_ID)
    /** Práctica que suena en susurro al abrir la app. Por defecto, el refugio. */
    val startupPracticeId: StateFlow<String> = _startupPracticeId.asStateFlow()

    fun setStartupPracticeId(practiceId: String?) {
        val id = practiceId ?: REFUGE_PRACTICE_ID
        prefs.edit { putString(KEY_STARTUP_PRACTICE, id) }
        _startupPracticeId.value = id
    }

    private val _voice = MutableStateFlow(readVoice())
    /** Voz, tono, volumen y ecualización con que se leen las prácticas. */
    val voice: StateFlow<VoiceSettings> = _voice.asStateFlow()

    fun setVoice(settings: VoiceSettings) {
        val v = settings.normalized()
        prefs.edit {
            putString(KEY_VOICE_NAME, v.voiceName)
            putFloat(KEY_VOICE_PITCH, v.pitch)
            putFloat(KEY_VOICE_VOLUME, v.volume)
            putFloat(KEY_EQ_BASS, v.bassDb)
            putFloat(KEY_EQ_MID, v.midDb)
            putFloat(KEY_EQ_TREBLE, v.trebleDb)
        }
        _voice.value = v
    }

    private fun readVoice() = VoiceSettings(
        voiceName = prefs.getString(KEY_VOICE_NAME, null),
        pitch = prefs.getFloat(KEY_VOICE_PITCH, 1f),
        volume = prefs.getFloat(KEY_VOICE_VOLUME, 1f),
        bassDb = prefs.getFloat(KEY_EQ_BASS, 0f),
        midDb = prefs.getFloat(KEY_EQ_MID, 0f),
        trebleDb = prefs.getFloat(KEY_EQ_TREBLE, 0f),
    ).normalized()

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_ASKED, value) }

    companion object {
        const val PREFS_NAME = "mani_settings"
        private const val KEY_START_WITH_REFUGE = "start_with_refuge_whisper"
        private const val KEY_STARTUP_PRACTICE = "startup_practice_id"
        private const val KEY_NOTIF_ASKED = "notification_permission_asked"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_EQ_BASS = "voice_eq_bass_db"
        private const val KEY_EQ_MID = "voice_eq_mid_db"
        private const val KEY_EQ_TREBLE = "voice_eq_treble_db"
        const val REFUGE_PRACTICE_ID = "prac_refugio_tres_joyas"
    }
}
