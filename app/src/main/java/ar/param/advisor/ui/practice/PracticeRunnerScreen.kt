package ar.param.advisor.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.param.advisor.ManiApplication
import ar.param.advisor.R
import ar.param.advisor.engine.PlaybackOverrides
import ar.param.advisor.engine.PracticeRunSpec
import ar.param.advisor.engine.PracticeSegmentModel
import ar.param.advisor.engine.RecitationMode
import ar.param.advisor.engine.RunnerSnapshot
import ar.param.advisor.engine.RunnerStage
import ar.param.advisor.engine.RunnerStatus
import ar.param.advisor.service.PracticePlaybackService
import ar.param.advisor.service.rememberPracticeServiceState
import ar.param.advisor.ui.ManiViewModel
import ar.param.advisor.ui.home.practiceTypeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeRunnerScreen(practiceId: String, viewModel: ManiViewModel, onBack: () -> Unit) {
    val details by remember(practiceId) { viewModel.observePractice(practiceId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val spec by remember(practiceId) { viewModel.observePracticeSpec(practiceId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val serviceState by rememberPracticeServiceState()
    val lastSessionId by PracticePlaybackService.lastFinishedSessionId.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_screen_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        val current = spec
        val practice = details
        if (current == null || practice == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.practice_not_available), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            return@Scaffold
        }
        val isThis = serviceState.practiceId == practiceId
        val live = isThis && serviceState.isActive
        val completedHere = isThis && serviceState.status == RunnerStatus.COMPLETED &&
            serviceState.sessionStartedAtEpochMs != viewModel.dedicationClosedForSessionStart
        var stoppedHere by rememberSaveable(practiceId) { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(current, practice.practice.subtitle, practice.practice.tradition, practice.practice.type)

            if (completedHere || (stoppedHere && !live)) {
                DedicationCard(
                    dedication = current.meritDedication,
                    completed = completedHere,
                    onDedicate = {
                        lastSessionId?.let(viewModel::dedicateMeritForSession)
                        viewModel.dedicationClosedForSessionStart = serviceState.sessionStartedAtEpochMs
                        stoppedHere = false
                        onBack()
                    },
                    onReturn = {
                        viewModel.dedicationClosedForSessionStart = serviceState.sessionStartedAtEpochMs
                        stoppedHere = false
                        onBack()
                    },
                )
                return@Column
            }

            RunnerBody(
                spec = current,
                snapshot = serviceState,
                live = live,
                onStarted = { stoppedHere = false },
                onStopped = { stoppedHere = true },
            )
        }
    }
}

@Composable
private fun Header(spec: PracticeRunSpec, subtitle: String?, tradition: String, type: String) {
    Column {
        Text(spec.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            stringResource(R.string.practice_header_meta, stringResource(practiceTypeLabel(type)), tradition),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunnerBody(
    spec: PracticeRunSpec,
    snapshot: RunnerSnapshot,
    live: Boolean,
    onStarted: () -> Unit,
    onStopped: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ManiApplication
    var selectedMode by rememberSaveable(spec.practiceId) { mutableStateOf(RecitationMode.SPOKEN) }
    var selectedReps by rememberSaveable(spec.practiceId) { mutableIntStateOf(spec.mantraConfig?.targetRepetitions ?: 0) }
    var keepScreenOn by rememberSaveable { mutableStateOf(false) }
    val mode = if (live) snapshot.recitationMode else selectedMode

    val view = LocalView.current
    DisposableEffect(keepScreenOn, live) {
        view.keepScreenOn = keepScreenOn && live
        onDispose { view.keepScreenOn = false }
    }

    val start = {
        onStarted()
        PracticePlaybackService.startPractice(
            context,
            spec.practiceId,
            PlaybackOverrides(targetReps = selectedReps.takeIf { it > 0 }, mode = selectedMode),
        )
    }
    // El permiso de notificaciones se pide acá, al iniciar una práctica, nunca al abrir la app (REQ-009).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    val onStart = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !app.settings.notificationPermissionAsked
        if (needsPermission) {
            app.settings.notificationPermissionAsked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    Progress(spec, snapshot, live)

    val shownSegment: PracticeSegmentModel? = if (live) snapshot.currentSegment else spec.segments.firstOrNull()
    Crossfade(targetState = shownSegment, animationSpec = tween(450), label = "segmento") { segment ->
        if (segment != null) SegmentCard(segment)
    }

    if (live && snapshot.pauseRemainingSec > 0) {
        Text(
            stringResource(R.string.pause_indicator, snapshot.pauseRemainingSec),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = RecitationMode.entries
        modes.forEachIndexed { index, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = {
                    selectedMode = m
                    if (live) PracticePlaybackService.setMode(context, m)
                },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(stringResource(PracticePlaybackService.modeLabelRes(m)), maxLines = 2) }
        }
    }

    val presets = spec.mantraConfig?.presetCounts.orEmpty()
    if (!live && presets.isNotEmpty()) {
        Text(stringResource(R.string.reps_title), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { n ->
                FilterChip(
                    selected = selectedReps == n,
                    onClick = { selectedReps = n },
                    label = { Text(stringResource(R.string.reps_chip, n)) },
                )
            }
        }
    }

    Controls(
        status = if (live) snapshot.status else RunnerStatus.IDLE,
        onStart = onStart,
        onPause = { PracticePlaybackService.pause(context) },
        onResume = { PracticePlaybackService.resume(context) },
        onStop = {
            PracticePlaybackService.stop(context)
            onStopped()
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { keepScreenOn = !keepScreenOn }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.keep_screen_on), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = keepScreenOn, onCheckedChange = null)
    }

    Text(
        stringResource(R.string.background_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Progress(spec: PracticeRunSpec, snapshot: RunnerSnapshot, live: Boolean) {
    if (!live) return
    val parts = buildList {
        add(stringResource(R.string.progress_segment, snapshot.segmentIndex + 1, spec.segments.size))
        if (snapshot.stage == RunnerStage.MANTRA_LOOP || snapshot.mantraRepetition > 0) {
            add(stringResource(R.string.progress_round, snapshot.mantraRepetition, snapshot.mantraTargetRepetitions))
        } else if (snapshot.segmentTotalRepetitions > 1) {
            add(stringResource(R.string.progress_repetition, snapshot.segmentRepetition, snapshot.segmentTotalRepetitions))
        }
        if (snapshot.status == RunnerStatus.PAUSED) add(stringResource(R.string.progress_paused))
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun SegmentCard(segment: PracticeSegmentModel) {
    var showMeaning by rememberSaveable(segment.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(segment.heading, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(segment.text, style = MaterialTheme.typography.bodyLarge)
            segment.transliteration?.takeIf { it != segment.text }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            segment.meaning?.let { meaning ->
                TextButton(onClick = { showMeaning = !showMeaning }) {
                    Text(stringResource(if (showMeaning) R.string.meaning_hide else R.string.meaning_show))
                }
                if (showMeaning) {
                    Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Controls(
    status: RunnerStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (status) {
            RunnerStatus.PLAYING -> ControlButton(R.drawable.ic_pause_calm, R.string.action_pause, onPause, Modifier.weight(1f))
            RunnerStatus.PAUSED -> ControlButton(R.drawable.ic_play_calm, R.string.action_resume, onResume, Modifier.weight(1f))
            else -> ControlButton(R.drawable.ic_play_calm, R.string.action_start, onStart, Modifier.weight(1f))
        }
        if (status == RunnerStatus.PLAYING || status == RunnerStatus.PAUSED) {
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(painterResource(R.drawable.ic_stop_calm), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_stop))
            }
        }
    }
}

@Composable
private fun ControlButton(icon: Int, label: Int, onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier.height(52.dp)) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

@Composable
private fun DedicationCard(dedication: String, completed: Boolean, onDedicate: () -> Unit, onReturn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (completed) R.string.dedication_completed else R.string.dedication_stopped),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                dedication,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Button(onClick = onDedicate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_dedicate))
            }
            TextButton(onClick = onReturn, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_return_without_guilt))
            }
        }
    }
}
