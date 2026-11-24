package ar.param.advisor.data.settings

import android.content.Context
import androidx.core.content.edit
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

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_ASKED, value) }

    companion object {
        const val PREFS_NAME = "mani_settings"
        private const val KEY_START_WITH_REFUGE = "start_with_refuge_whisper"
        private const val KEY_NOTIF_ASKED = "notification_permission_asked"
        const val REFUGE_PRACTICE_ID = "prac_refugio_tres_joyas"
    }
}
