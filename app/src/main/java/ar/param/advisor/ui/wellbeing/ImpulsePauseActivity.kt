package ar.param.advisor.ui.wellbeing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.param.advisor.MainActivity
import ar.param.advisor.ManiApplication
import ar.param.advisor.digitalwellbeing.ImpulsePauseDecision
import ar.param.advisor.digitalwellbeing.toEvent
import ar.param.advisor.engine.PlaybackOverrides
import ar.param.advisor.engine.RecitationMode
import ar.param.advisor.service.PracticePlaybackService
import ar.param.advisor.ui.theme.ManiTheme
import kotlinx.coroutines.launch
import java.util.UUID

/** Pausa lúcida al abrir una app marcada, o desde el freno manual (sin extras). */
class ImpulsePauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val appName = intent.getStringExtra(EXTRA_APP_DISPLAY_NAME)
        setContent {
            ManiTheme {
                ImpulsePauseScreen(
                    appName = appName,
                    isManual = pkg == null,
                    onRecord = ::record,
                    onRestInCalm = ::restInCalm,
                    onContinue = { finish() },
                    onListen = ::listen,
                    onCloseManual = { finish() },
                    packageName = pkg,
                )
            }
        }
    }

    private fun record(decision: ImpulsePauseDecision) {
        val app = application as ManiApplication
        val event = decision.toEvent(UUID.randomUUID().toString(), System.currentTimeMillis())
        app.applicationScope.launch { runCatching { app.meritAndCalmDao.insertEvent(event) } }
    }

    /** Deja la app de lado y vuelve a la pantalla de inicio del teléfono. */
    private fun restInCalm() {
        val isManual = intent.getStringExtra(EXTRA_PACKAGE_NAME) == null
        if (!isManual) {
            runCatching {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    /** REQ-013: inicia la práctica sugerida y abre Mani en esa pantalla. */
    private fun listen(practiceId: String, targetReps: Int?) {
        PracticePlaybackService.startPractice(
            this,
            practiceId,
            PlaybackOverrides(targetReps = targetReps, mode = RecitationMode.SPOKEN),
        )
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PRACTICE_ID, practiceId),
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "ar.param.advisor.extra.PACKAGE_NAME"
        const val EXTRA_APP_DISPLAY_NAME = "ar.param.advisor.extra.APP_DISPLAY_NAME"

        fun intent(context: Context, packageName: String? = null, displayName: String? = null): Intent =
            Intent(context, ImpulsePauseActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply {
                    packageName?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
                    displayName?.let { putExtra(EXTRA_APP_DISPLAY_NAME, it) }
                }
    }
}
