package ar.param.advisor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.param.advisor.ManiApplication
import ar.param.advisor.data.local.dao.PracticeDao
import ar.param.advisor.data.local.dao.PracticeSessionDao
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.local.entity.PracticeWithDetails
import ar.param.advisor.data.settings.ManiSettings
import ar.param.advisor.engine.PracticeRunSpec
import ar.param.advisor.engine.RunnerSnapshot
import ar.param.advisor.engine.toPracticeRunSpec
import ar.param.advisor.service.PracticePlaybackService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class RefugeHomeUiState(
    val isLoading: Boolean = true,
    val featuredPractice: PracticeWithDetails? = null,
    val practices: List<PracticeWithDetails> = emptyList(),
    val impulsePractice: PracticeWithDetails? = null,
    val activeRunnerSnapshot: RunnerSnapshot = RunnerSnapshot(),
    val practiceMomentsThisWeek: Int = 0,
    val startWithRefuge: Boolean = true,
)

class ManiViewModel(
    private val practiceDao: PracticeDao,
    private val sessionDao: PracticeSessionDao,
    private val settings: ManiSettings,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** Recalculado en cada emisión: si cambia el día con la app abierta, se usa la fecha nueva (REQ-013). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val weekMoments: Flow<Int> = flow { emit(weekStartEpochMs()) }
        .flatMapLatest { sessionDao.observeSessionsCountSince(it) }

    val homeUiState: StateFlow<RefugeHomeUiState> = combine(
        practiceDao.observeAllPracticesWithDetails(),
        PracticePlaybackService.sessionState,
        weekMoments,
        settings.startWithRefuge,
    ) { all, snapshot, moments, startWithRefuge ->
        val runnable = all.filter { it.practice.type != PracticeTypes.IMPULSE_PAUSE }
        RefugeHomeUiState(
            isLoading = all.isEmpty(),
            featuredPractice = featuredFor(runnable),
            practices = runnable.sortedWith(compareByDescending<PracticeWithDetails> { it.practice.isFavorite }),
            impulsePractice = all.firstOrNull { it.practice.type == PracticeTypes.IMPULSE_PAUSE },
            activeRunnerSnapshot = snapshot,
            practiceMomentsThisWeek = moments,
            startWithRefuge = startWithRefuge,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefugeHomeUiState())

    /** Rotación determinista por fecha (REQ-031, propuesta de Q-06). */
    private fun featuredFor(practices: List<PracticeWithDetails>): PracticeWithDetails? {
        if (practices.isEmpty()) return null
        val sorted = practices.sortedBy { it.practice.id }
        return sorted[(today().toEpochDay() % sorted.size).toInt()]
    }

    private fun weekStartEpochMs(): Long =
        today().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Inicio de la sesión cuya tarjeta de dedicación ya se cerró (para no volver a mostrarla). */
    var dedicationClosedForSessionStart by mutableLongStateOf(-1L)

    fun observePractice(practiceId: String): Flow<PracticeWithDetails?> = practiceDao.observePracticeById(practiceId)

    fun observePracticeSpec(practiceId: String): Flow<PracticeRunSpec?> =
        observePractice(practiceId).map { it?.toPracticeRunSpec() }

    fun toggleFavorite(practice: PracticeWithDetails) {
        viewModelScope.launch { practiceDao.setFavorite(practice.practice.id, !practice.practice.isFavorite) }
    }

    fun dedicateMeritForSession(sessionId: String) {
        viewModelScope.launch { sessionDao.markMeritDedicated(sessionId) }
    }

    fun setStartWithRefuge(enabled: Boolean) = settings.setStartWithRefuge(enabled)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ManiApplication
                ManiViewModel(app.practiceDao, app.practiceSessionDao, app.settings)
            }
        }
    }
}
