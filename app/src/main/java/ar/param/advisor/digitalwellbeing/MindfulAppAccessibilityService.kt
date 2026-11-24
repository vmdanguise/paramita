package ar.param.advisor.digitalwellbeing

import android.accessibilityservice.AccessibilityService
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import ar.param.advisor.ManiApplication
import ar.param.advisor.ui.wellbeing.ImpulsePauseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Solo mira qué paquete pasa a primer plano (SEC-001): no lee contenido de ventanas
 * ni guarda historial. Todo lo demás lo decide [MindfulAppTriggerEvaluator].
 */
class MindfulAppAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    private lateinit var evaluator: MindfulAppTriggerEvaluator
    private var ignored: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as ManiApplication
        evaluator = MindfulAppTriggerEvaluator(
            app.mindfulAppTargetDao,
            ImpulseCooldownTracker(SharedPrefsCooldownStore(this)),
        )
        ignored = buildSet {
            add(packageName)
            add("com.android.systemui")
            packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let(::add)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored || !::evaluator.isInitialized) return
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            if (isDriving()) return@launch
            val target = runCatching { evaluator.evaluateForegroundPackage(pkg, packageName) }
                .onFailure { Log.w(TAG, "No se pudo evaluar la app", it) }
                .getOrNull() ?: return@launch
            startActivity(ImpulsePauseActivity.intent(this@MindfulAppAccessibilityService, target.packageName, target.displayName))
        }
    }

    /** REQ-025 / SEC-010: con el teléfono en modo auto (Android Auto) no se interrumpe. */
    private fun isDriving(): Boolean {
        val ui = getSystemService(UiModeManager::class.java) ?: return false
        return ui.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MindfulApps"
        const val DEBOUNCE_MS = 350L
    }
}
