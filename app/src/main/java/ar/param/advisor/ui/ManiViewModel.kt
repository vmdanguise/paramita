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
import ar.param.advisor.data.local.dao.MindfulAppTargetDao
import ar.param.advisor.data.local.dao.PracticeDao
import ar.param.advisor.data.local.entity.MindfulAppTargetEntity
import ar.param.advisor.data.local.dao.PracticeSessionDao
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.local.entity.PracticeWithDetails
import ar.param.advisor.data.settings.ManiSettings
import ar.param.advisor.data.user.UserMediaStore
import ar.param.advisor.data.user.UserPracticeForm
import ar.param.advisor.engine.PracticeRunSpec
import ar.param.advisor.engine.RunnerSnapshot
import ar.param.advisor.engine.VoiceSettings
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
    val myPractices: List<PracticeWithDetails> = emptyList(),
    val impulsePractice: PracticeWithDetails? = null,
    val activeRunnerSnapshot: RunnerSnapshot = RunnerSnapshot(),
    val practiceMomentsThisWeek: Int = 0,
    val startWithRefuge: Boolean = true,
    val startupPracticeTitle: String? = null,
)

class ManiViewModel(
    private val practiceDao: PracticeDao,
    private val sessionDao: PracticeSessionDao,
    private val targetDao: MindfulAppTargetDao,
    private val settings: ManiSettings,
    private val media: UserMediaStore,
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
        settings.startupPracticeId,
    ) { all, snapshot, moments, startWithRefuge, startupId ->
        val runnable = all.filter { it.practice.type != PracticeTypes.IMPULSE_PAUSE }
        val (mine, catalog) = runnable.partition { it.practice.isUserCreated }
        RefugeHomeUiState(
            isLoading = all.isEmpty(),
            featuredPractice = featuredFor(catalog),
            practices = catalog.sortedWith(compareByDescending<PracticeWithDetails> { it.practice.isFavorite }),
            myPractices = mine.sortedWith(compareByDescending<PracticeWithDetails> { it.practice.isFavorite }),
            impulsePractice = all.firstOrNull { it.practice.type == PracticeTypes.IMPULSE_PAUSE },
            activeRunnerSnapshot = snapshot,
            practiceMomentsThisWeek = moments,
            startWithRefuge = startWithRefuge,
            startupPracticeTitle = all.firstOrNull { it.practice.id == startupId }?.practice?.title,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefugeHomeUiState())

    val startupPracticeId: StateFlow<String> = settings.startupPracticeId

    fun setStartupPractice(practiceId: String?) = settings.setStartupPracticeId(practiceId)

    val voiceSettings: StateFlow<VoiceSettings> = settings.voice

    fun setVoiceSettings(voice: VoiceSettings) = settings.setVoice(voice)

    /** Carga una práctica propia para editarla, o un formulario vacío si es nueva. */
    suspend fun loadForm(practiceId: String?, defaultDedication: String): UserPracticeForm? {
        if (practiceId == null) return UserPracticeForm()
        val details = practiceDao.getPracticeByIdOnce(practiceId) ?: return null
        if (!details.practice.isUserCreated) return null
        return UserPracticeForm.from(details, defaultDedication)
    }

    /** Guarda la práctica y borra la imagen o grabación anterior si se reemplazó. */
    fun saveUserPractice(
        form: UserPracticeForm,
        defaultDedication: String,
        tradition: String,
        previous: UserPracticeForm?,
        playOnLaunch: Boolean,
        onSaved: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val bundle = form.toEntities(defaultDedication, tradition)
            practiceDao.saveUserPractice(bundle.practice, bundle.segments, bundle.mantraConfig)
            if (previous != null) {
                if (previous.imagePath != form.imagePath) media.deleteOwned(previous.imagePath)
                if (previous.recordingPath != form.recordingPath) media.deleteOwned(previous.recordingPath)
            }
            if (playOnLaunch) {
                settings.setStartupPracticeId(form.id)
            } else if (settings.startupPracticeId.value == form.id) {
                settings.setStartupPracticeId(null)
            }
            onSaved(form.id)
        }
    }

    /** Se archiva (ADR-0003): el historial de esa práctica sigue existiendo. */
    fun deleteUserPractice(form: UserPracticeForm, onDone: () -> Unit) {
        viewModelScope.launch {
            practiceDao.archiveUserPractice(form.id)
            media.deleteOwned(form.imagePath)
            media.deleteOwned(form.recordingPath)
            if (settings.startupPracticeId.value == form.id) settings.setStartupPracticeId(null)
            onDone()
        }
    }

    /** Archivos elegidos o grabados que quedaron sin usar porque se canceló la edición. */
    fun discardMedia(vararg paths: String?) = paths.forEach(media::deleteOwned)

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

    val mindfulAppTargets: StateFlow<List<MindfulAppTargetEntity>> = targetDao.observeTargets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveMindfulAppTarget(target: MindfulAppTargetEntity) {
        viewModelScope.launch { targetDao.upsertTarget(target.copy(cooldownMinutes = target.cooldownMinutes.coerceIn(1, 1440))) }
    }

    fun deleteMindfulAppTarget(packageName: String) {
        viewModelScope.launch { targetDao.deleteTarget(packageName) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ManiApplication
                ManiViewModel(app.practiceDao, app.practiceSessionDao, app.mindfulAppTargetDao, app.settings, app.userMedia)
            }
        }
    }
}
