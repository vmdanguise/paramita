package ar.param.advisor.digitalwellbeing

import ar.param.advisor.data.local.dao.MindfulAppTargetDao
import ar.param.advisor.data.local.entity.MindfulAppTargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class MemoryCooldownStore : CooldownStore {
    val map = mutableMapOf<String, Long>()
    override fun lastShown(pkg: String) = map[pkg]
    override fun markShown(pkg: String, at: Long) {
        map[pkg] = at
    }
}

private class FakeTargetDao(vararg targets: MindfulAppTargetEntity) : MindfulAppTargetDao {
    val byPkg = targets.associateBy { it.packageName }.toMutableMap()
    override fun observeTargets(): Flow<List<MindfulAppTargetEntity>> = flowOf(byPkg.values.toList())
    override suspend fun getEnabledTargetsOnce() = byPkg.values.filter { it.promptEnabled }
    override suspend fun getTargetByPackage(packageName: String) = byPkg[packageName]
    override suspend fun upsertTarget(target: MindfulAppTargetEntity) {
        byPkg[target.packageName] = target
    }
    override suspend fun upsertTargets(targets: List<MindfulAppTargetEntity>) = targets.forEach { byPkg[it.packageName] = it }
    override suspend fun updateTargetRule(packageName: String, enabled: Boolean, cooldownMinutes: Int) {
        byPkg[packageName]?.let { byPkg[packageName] = it.copy(promptEnabled = enabled, cooldownMinutes = cooldownMinutes) }
    }
    override suspend fun deleteTarget(packageName: String) {
        byPkg.remove(packageName)
    }
}

class ImpulseTriggerTest {

    private val ml = MindfulAppTargetEntity("com.mercadolibre", "Mercado Libre", AppCategories.SHOPPING, true, 1)
    private val fb = MindfulAppTargetEntity("com.facebook.katana", "Facebook", AppCategories.SOCIAL_SCROLL, false, 15)
    private val own = "ar.param.advisor"

    @Test
    fun ac001_ac002_showsOnceAndNotAgainInsideCooldown() = runBlocking {
        val store = MemoryCooldownStore()
        val evaluator = MindfulAppTriggerEvaluator(FakeTargetDao(ml), ImpulseCooldownTracker(store))
        assertNotNull(evaluator.evaluateForegroundPackage("com.mercadolibre", own, 0L))
        // Al volver con "Continuar con consciencia" no hay bucle.
        assertNull(evaluator.evaluateForegroundPackage("com.mercadolibre", own, 2_000L))
        assertNull(evaluator.evaluateForegroundPackage("com.mercadolibre", own, 59_999L))
        assertNotNull(evaluator.evaluateForegroundPackage("com.mercadolibre", own, 60_000L))
    }

    @Test
    fun ac003_cooldownSurvivesNewEvaluatorWithSameStore() = runBlocking {
        val store = MemoryCooldownStore()
        MindfulAppTriggerEvaluator(FakeTargetDao(ml), ImpulseCooldownTracker(store))
            .evaluateForegroundPackage("com.mercadolibre", own, 0L)
        val restarted = MindfulAppTriggerEvaluator(FakeTargetDao(ml), ImpulseCooldownTracker(store))
        assertNull(restarted.evaluateForegroundPackage("com.mercadolibre", own, 30_000L))
    }

    @Test
    fun ac004_disabledTargetNeverShows() = runBlocking {
        val evaluator = MindfulAppTriggerEvaluator(FakeTargetDao(fb), ImpulseCooldownTracker(MemoryCooldownStore()))
        assertNull(evaluator.evaluateForegroundPackage("com.facebook.katana", own, 0L))
    }

    @Test
    fun ignoresOwnPackageNullAndUnknownApps() = runBlocking {
        val evaluator = MindfulAppTriggerEvaluator(FakeTargetDao(ml), ImpulseCooldownTracker(MemoryCooldownStore()))
        assertNull(evaluator.evaluateForegroundPackage(null, own, 0L))
        assertNull(evaluator.evaluateForegroundPackage(own, own, 0L))
        assertNull(evaluator.evaluateForegroundPackage("com.whatsapp", own, 0L))
    }

    @Test
    fun zeroCooldownIsTreatedAsOneMinute() {
        val tracker = ImpulseCooldownTracker(MemoryCooldownStore())
        tracker.markShown("p", 0L)
        assertEquals(false, tracker.shouldTrigger("p", 0, 30_000L))
        assertEquals(true, tracker.shouldTrigger("p", 0, 60_000L))
    }

    @Test
    fun ac005_impulseBuyRestInCalmRecordsAvoidedAmount() {
        val event = ImpulsePauseDecision(
            packageName = "com.mercadolibre",
            displayContext = "Mercado Libre",
            emotion = ImpulseEmotion.IMPULSE_BUY,
            outcome = PauseOutcome.PAUSED_AND_LEFT_APP,
            avoidedAmountArs = 25_000.0,
        ).toEvent("e1", 10L)
        assertEquals(MeritEventTypes.IMPULSE_PURCHASE_AVOIDED, event.eventType)
        assertEquals(25_000.0, event.avoidedAmountEstimate!!, 0.0)
        assertEquals("ARS", event.currencyCode)
        assertEquals("PAUSED_AND_LEFT_APP", event.outcome)
    }

    @Test
    fun continuingConsciouslyIsACalmBreakWithoutAmount() {
        val event = ImpulsePauseDecision(
            packageName = "com.mercadolibre",
            displayContext = "Mercado Libre",
            emotion = ImpulseEmotion.IMPULSE_BUY,
            outcome = PauseOutcome.PROCEEDED_CONSCIOUSLY,
            avoidedAmountArs = 25_000.0,
        ).toEvent("e2", 10L)
        assertEquals(MeritEventTypes.DIGITAL_CALM_BREAK, event.eventType)
        assertNull(event.avoidedAmountEstimate)
        assertNull(event.currencyCode)
    }
}
