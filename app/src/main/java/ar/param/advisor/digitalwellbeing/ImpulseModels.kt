package ar.param.advisor.digitalwellbeing

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import ar.param.advisor.data.local.dao.MindfulAppTargetDao
import ar.param.advisor.data.local.entity.MeritAndCalmEventEntity
import ar.param.advisor.data.local.entity.MindfulAppTargetEntity

enum class ImpulseEmotion { ANXIETY, TIREDNESS, BOREDOM, IMPULSE_BUY }

enum class PauseOutcome { PRACTICED_MAITRI, PAUSED_AND_LEFT_APP, PROCEEDED_CONSCIOUSLY }

object AppCategories {
    const val SHOPPING = "SHOPPING"
    const val SOCIAL_SCROLL = "SOCIAL_SCROLL"
    const val OTHER = "OTHER"
    val ALL = listOf(SHOPPING, SOCIAL_SCROLL, OTHER)
}

object MeritEventTypes {
    const val IMPULSE_PURCHASE_AVOIDED = "IMPULSE_PURCHASE_AVOIDED"
    const val DIGITAL_CALM_BREAK = "DIGITAL_CALM_BREAK"
}

const val IMPULSE_DEDICATION = "Que la serenidad y el cuidado de hoy beneficien a todos los seres."

data class ImpulsePauseDecision(
    val packageName: String?,
    val displayContext: String,
    val emotion: ImpulseEmotion,
    val outcome: PauseOutcome,
    val avoidedAmountArs: Double? = null,
    val meritDedication: String = IMPULSE_DEDICATION,
)

/** REQ-002: tipo de evento y monto según la emoción y el resultado. */
fun ImpulsePauseDecision.toEvent(id: String, nowEpochMs: Long): MeritAndCalmEventEntity {
    val avoided = emotion == ImpulseEmotion.IMPULSE_BUY && outcome != PauseOutcome.PROCEEDED_CONSCIOUSLY
    val amount = avoidedAmountArs?.takeIf { avoided && it > 0 }
    return MeritAndCalmEventEntity(
        id = id,
        eventType = if (avoided) MeritEventTypes.IMPULSE_PURCHASE_AVOIDED else MeritEventTypes.DIGITAL_CALM_BREAK,
        title = displayContext,
        triggerContext = packageName ?: "manual",
        avoidedAmountEstimate = amount,
        currencyCode = amount?.let { "ARS" },
        emotionNoticed = emotion.name,
        dedicationNote = meritDedication,
        outcome = outcome.name,
        createdAtEpochMs = nowEpochMs,
    )
}

/** Práctica sugerida según la emoción (spec §4.2). */
fun suggestedPracticeFor(emotion: ImpulseEmotion): String = when (emotion) {
    ImpulseEmotion.ANXIETY -> "prac_med_amor_bondadoso"
    ImpulseEmotion.TIREDNESS -> "prac_calma_digital_noche"
    ImpulseEmotion.BOREDOM -> "prac_37_practicas_refugio_y_impermanencia"
    ImpulseEmotion.IMPULSE_BUY -> "prac_pausa_impulso_compra"
}

interface CooldownStore {
    fun lastShown(pkg: String): Long?
    fun markShown(pkg: String, at: Long)
}

/** REQ-023: el cooldown sobrevive al reinicio del proceso. Solo guarda el último momento por paquete. */
class SharedPrefsCooldownStore(context: Context) : CooldownStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun lastShown(pkg: String): Long? = prefs.getLong(pkg, -1L).takeIf { it >= 0 }
    override fun markShown(pkg: String, at: Long) = prefs.edit { putLong(pkg, at) }

    companion object {
        const val PREFS_NAME = "impulse_cooldown_prefs"
    }
}

class ImpulseCooldownTracker(private val store: CooldownStore) {
    fun shouldTrigger(packageName: String, cooldownMinutes: Int, nowEpochMs: Long): Boolean {
        val last = store.lastShown(packageName) ?: return true
        // Si el reloj fue hacia atrás, no se bloquea para siempre.
        if (nowEpochMs < last) return true
        return nowEpochMs - last >= cooldownMinutes.coerceAtLeast(1) * 60_000L
    }

    fun markShown(packageName: String, nowEpochMs: Long) = store.markShown(packageName, nowEpochMs)
}

class MindfulAppTriggerEvaluator(
    private val targetDao: MindfulAppTargetDao,
    private val cooldownTracker: ImpulseCooldownTracker,
) {
    /** Marca el cooldown **antes** de devolver el target, así al volver de la pausa no hay bucle (REQ-022). */
    suspend fun evaluateForegroundPackage(
        packageName: String?,
        ownPackageName: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): MindfulAppTargetEntity? {
        if (packageName.isNullOrBlank() || packageName == ownPackageName) return null
        val target = targetDao.getTargetByPackage(packageName) ?: return null
        if (!target.promptEnabled) return null
        if (!cooldownTracker.shouldTrigger(packageName, target.cooldownMinutes, nowEpochMs)) return null
        cooldownTracker.markShown(packageName, nowEpochMs)
        return target
    }
}

fun isMindfulAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val ours = ComponentName(context, MindfulAppAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}

/**
 * Apps comunes de compras y scroll, para sugerirlas si están instaladas.
 * Solo se usan para ordenar la lista: si un paquete no existe, no aparece.
 */
val SUGGESTED_TARGETS: Map<String, String> = mapOf(
    "com.mercadolibre" to AppCategories.SHOPPING,
    "com.amazon.mShop.android.shopping" to AppCategories.SHOPPING,
    "com.alibaba.aliexpresshd" to AppCategories.SHOPPING,
    "com.einnovation.temu" to AppCategories.SHOPPING,
    "com.zzkko" to AppCategories.SHOPPING,
    "com.facebook.katana" to AppCategories.SOCIAL_SCROLL,
    "com.facebook.lite" to AppCategories.SOCIAL_SCROLL,
    "com.instagram.android" to AppCategories.SOCIAL_SCROLL,
    "com.zhiliaoapp.musically" to AppCategories.SOCIAL_SCROLL,
    "com.ss.android.ugc.trill" to AppCategories.SOCIAL_SCROLL,
    "com.twitter.android" to AppCategories.SOCIAL_SCROLL,
    "com.google.android.youtube" to AppCategories.SOCIAL_SCROLL,
    "com.reddit.frontpage" to AppCategories.SOCIAL_SCROLL,
)
