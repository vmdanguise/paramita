package ar.param.advisor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import ar.param.advisor.data.settings.ManiSettings
import ar.param.advisor.engine.PlaybackOverrides
import ar.param.advisor.engine.RecitationMode
import ar.param.advisor.service.PracticePlaybackService
import ar.param.advisor.ui.ManiViewModel
import ar.param.advisor.ui.navigation.ManiNavGraph
import ar.param.advisor.ui.navigation.NavRequest
import ar.param.advisor.ui.theme.ManiTheme
import kotlinx.coroutines.launch

/**
 * Punto de entrada. No pide permisos al abrir (AC-005): POST_NOTIFICATIONS se pide
 * la primera vez que el usuario toca "Comenzar" (REQ-009).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ManiViewModel by viewModels { ManiViewModel.Factory }
    private var navRequest by mutableStateOf<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            if (!handleOpenActive(intent)) maybeStartRefugeWhisper()
        }
        setContent {
            ManiTheme {
                ManiNavGraph(
                    viewModel = viewModel,
                    navRequest = navRequest,
                    onNavRequestHandled = { navRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenActive(intent)
    }

    private fun handleOpenActive(intent: Intent?): Boolean {
        intent?.getStringExtra(EXTRA_OPEN_PRACTICE_ID)?.let {
            navRequest = NavRequest(it)
            return true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ACTIVE_PRACTICE, false) != true) return false
        val id = PracticePlaybackService.sessionState.value.practiceId ?: return false
        navRequest = NavRequest(id)
        return true
    }

    /**
     * Al abrir la app, recita en susurro la oración de refugio (pedido de Vicente).
     * Se puede desactivar desde Inicio. Si ya hay una práctica sonando, no la interrumpe.
     */
    private fun maybeStartRefugeWhisper() {
        val app = application as ManiApplication
        if (!app.settings.startWithRefuge.value) return
        val active = PracticePlaybackService.sessionState.value
        if (active.isActive) {
            active.practiceId?.let { navRequest = NavRequest(it) }
            return
        }
        lifecycleScope.launch {
            app.catalogSeed.await()
            // La práctica elegida para el inicio (puede ser una propia); si se archivó, el refugio.
            val chosen = app.settings.startupPracticeId.value
            val practiceId = listOf(chosen, ManiSettings.REFUGE_PRACTICE_ID)
                .distinct()
                .firstOrNull { app.practiceDao.getPracticeByIdOnce(it) != null }
                ?: return@launch
            // Con la Activity visible: Android no permite iniciar un servicio en primer plano desde segundo plano.
            withResumed {
                if (PracticePlaybackService.sessionState.value.isActive) return@withResumed
                PracticePlaybackService.startPractice(
                    this@MainActivity,
                    practiceId,
                    PlaybackOverrides(mode = RecitationMode.WHISPER),
                )
                navRequest = NavRequest(practiceId)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ACTIVE_PRACTICE = "ar.param.advisor.extra.OPEN_ACTIVE_PRACTICE"
        const val EXTRA_OPEN_PRACTICE_ID = "ar.param.advisor.extra.OPEN_PRACTICE_ID"
    }
}
