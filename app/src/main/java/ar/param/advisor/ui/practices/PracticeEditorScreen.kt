package ar.param.advisor.ui.practices

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.param.advisor.ManiApplication
import ar.param.advisor.R
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.user.MantraRecorder
import ar.param.advisor.data.user.UserPracticeForm
import ar.param.advisor.ui.ManiViewModel
import ar.param.advisor.ui.components.PracticeArtwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private const val TRADITION_MINE = "Práctica propia"

/** Alta, edición y baja (archivo) de prácticas propias: oraciones y mantras en texto o grabados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeEditorScreen(
    practiceId: String?,
    viewModel: ManiViewModel,
    onDone: (savedId: String?) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ManiApplication
    val scope = rememberCoroutineScope()
    val defaultDedication = stringResource(R.string.editor_dedication_default)
    val startupId by viewModel.startupPracticeId.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<UserPracticeForm?>(null) }
    var form by remember { mutableStateOf(UserPracticeForm()) }
    var playOnLaunch by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    var showProblem by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(practiceId) {
        val f = viewModel.loadForm(practiceId, defaultDedication)
        if (f == null) {
            notFound = true
        } else {
            form = f
            original = if (practiceId != null) f else null
            playOnLaunch = practiceId != null && startupId == practiceId
        }
        loaded = true
    }

    val recorder = remember { MantraRecorder(context) }
    DisposableEffect(Unit) { onDispose { recorder.release() } }
    var recording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        recordingSeconds = 0
        while (recording) {
            delay(1_000L)
            recordingSeconds++
        }
    }

    /** Archivos nuevos de esta edición: si se cancela, se borran. */
    fun replaceImage(path: String?) {
        if (form.imagePath != original?.imagePath) viewModel.discardMedia(form.imagePath)
        form = form.copy(imagePath = path)
    }

    fun replaceRecording(path: String?) {
        if (form.recordingPath != original?.recordingPath) viewModel.discardMedia(form.recordingPath)
        form = form.copy(recordingPath = path)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = app.userMedia.importImage(uri)
                if (path == null) message = R.string.editor_image_error else replaceImage(path)
            }
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = app.userMedia.importAudio(uri)
                if (path == null) message = R.string.editor_recording_import_error else replaceRecording(path)
            }
        }
    }

    fun startRecording() {
        recorder.stopPlayback()
        listening = false
        if (recorder.start(app.userMedia.newRecordingFile())) {
            recording = true
        } else {
            message = R.string.editor_recording_error
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message = R.string.editor_mic_denied
    }

    fun cancel() {
        recorder.cancel()
        // Lo nuevo que no se guardó no queda ocupando lugar.
        if (form.imagePath != original?.imagePath) viewModel.discardMedia(form.imagePath)
        if (form.recordingPath != original?.recordingPath) viewModel.discardMedia(form.recordingPath)
        onDone(null)
    }

    BackHandler { cancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (practiceId == null) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = { TextButton(onClick = ::cancel) { Text(stringResource(R.string.editor_cancel)) } },
                actions = {
                    TextButton(
                        enabled = loaded && !notFound && !recording,
                        onClick = {
                            if (form.problem() != null) {
                                showProblem = true
                            } else {
                                viewModel.saveUserPractice(form, defaultDedication, TRADITION_MINE, original, playOnLaunch) { onDone(it) }
                            }
                        },
                    ) { Text(stringResource(R.string.editor_save)) }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        if (notFound) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.editor_not_found), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(16.dp))
                Button(onClick = { onDone(null) }) { Text(stringResource(R.string.action_back)) }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = listOf(PracticeTypes.PRAYER to R.string.type_prayer, PracticeTypes.MANTRA to R.string.type_mantra)
                types.forEachIndexed { i, (type, label) ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = {
                            form = if (type == PracticeTypes.MANTRA && form.type != type) {
                                form.copy(type = type, repetitions = form.repetitions.coerceAtLeast(21), pauseSec = form.pauseSec.coerceAtMost(2))
                            } else {
                                form.copy(type = type)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) { Text(stringResource(label)) }
                }
            }

            OutlinedTextField(
                value = form.title,
                onValueChange = { form = form.copy(title = it.take(120)) },
                label = { Text(stringResource(R.string.editor_field_title)) },
                singleLine = true,
                isError = showProblem && form.title.isBlank(),
                supportingText = if (showProblem && form.title.isBlank()) {
                    { Text(stringResource(R.string.editor_problem_title)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.text,
                onValueChange = { form = form.copy(text = it.take(4_000)) },
                label = { Text(stringResource(R.string.editor_field_text)) },
                minLines = 3,
                isError = showProblem && form.problem() == UserPracticeForm.Problem.NO_TEXT,
                supportingText = {
                    Text(
                        stringResource(
                            if (showProblem && form.problem() == UserPracticeForm.Problem.NO_TEXT) R.string.editor_problem_text else R.string.editor_field_text_hint,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            RecordingSection(
                recordingPath = form.recordingPath,
                recording = recording,
                seconds = recordingSeconds,
                listening = listening,
                onRecord = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStopRecording = {
                    recording = false
                    val path = recorder.stop()
                    if (path == null) message = R.string.editor_recording_error else replaceRecording(path)
                },
                onListen = {
                    val path = form.recordingPath
                    if (listening || path == null) {
                        recorder.stopPlayback()
                        listening = false
                    } else {
                        listening = true
                        recorder.play(path) { listening = false }
                    }
                },
                onRemove = {
                    recorder.stopPlayback()
                    listening = false
                    replaceRecording(null)
                },
                onImport = { pickAudio.launch("audio/*") },
            )

            HorizontalDivider()
            val reps = form.repetitions
            NumberStepper(
                label = stringResource(if (form.isMantra) R.string.editor_repetitions_mantra else R.string.editor_repetitions_prayer),
                value = reps,
                display = reps.toString(),
                onChange = { form = form.copy(repetitions = it.coerceIn(1, UserPracticeForm.MAX_REPETITIONS)) },
                steps = if (form.isMantra) listOf(1, 7, 21) else listOf(1),
                presets = if (form.isMantra) listOf(3, 7, 21, 108) else listOf(1, 3, 7),
            )
            NumberStepper(
                label = stringResource(if (form.isMantra) R.string.editor_pause_mantra else R.string.editor_pause_prayer),
                value = form.pauseSec,
                display = stringResource(R.string.editor_seconds, form.pauseSec),
                onChange = { form = form.copy(pauseSec = it.coerceIn(0, UserPracticeForm.MAX_PAUSE_SEC)) },
                steps = listOf(1, 5),
                presets = listOf(0, 2, 5, 10, 30),
            )
            if (form.isMantra) {
                NumberStepper(
                    label = stringResource(R.string.editor_bell),
                    value = form.bellEveryN,
                    display = if (form.bellEveryN == 0) stringResource(R.string.editor_bell_never) else stringResource(R.string.editor_bell_every, form.bellEveryN),
                    onChange = { form = form.copy(bellEveryN = it.coerceIn(0, reps)) },
                    steps = listOf(1, 7),
                    presets = listOf(0, 7, 21, 27),
                )
            }

            SpeedSlider(
                paceWpm = form.paceWpm,
                onChange = { form = form.copy(paceWpm = it) },
            )

            HorizontalDivider()
            ImageSection(
                imagePath = form.imagePath,
                fallbackKey = form.id,
                onPick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = { replaceImage(null) },
            )

            HorizontalDivider()
            OutlinedTextField(
                value = form.dedication,
                onValueChange = { form = form.copy(dedication = it.take(600)) },
                label = { Text(stringResource(R.string.editor_dedication)) },
                supportingText = { Text(stringResource(R.string.editor_dedication_hint, defaultDedication)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { playOnLaunch = !playOnLaunch }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_play_on_launch), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.editor_play_on_launch_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = playOnLaunch, onCheckedChange = null)
            }

            if (original != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    message?.let { res ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.pause_close)) } },
            text = { Text(stringResource(res)) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    recorder.release()
                    val current = original ?: return@TextButton
                    // Lo recién elegido en esta edición también se descarta.
                    if (form.imagePath != current.imagePath) viewModel.discardMedia(form.imagePath)
                    if (form.recordingPath != current.recordingPath) viewModel.discardMedia(form.recordingPath)
                    viewModel.deleteUserPractice(current) { onDone(null) }
                }) { Text(stringResource(R.string.editor_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.editor_cancel)) } },
        )
    }
}

@Composable
private fun RecordingSection(
    recordingPath: String?,
    recording: Boolean,
    seconds: Int,
    listening: Boolean,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onListen: () -> Unit,
    onRemove: () -> Unit,
    onImport: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.editor_recording), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.editor_recording_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                recording -> {
                    Text(
                        stringResource(R.string.editor_recording_now, "%d:%02d".format(seconds / 60, seconds % 60)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onStopRecording, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.editor_record_stop))
                    }
                }
                recordingPath != null -> {
                    Text(stringResource(R.string.editor_recording_ready), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onListen, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (listening) R.string.editor_recording_stop_listen else R.string.editor_recording_listen))
                        }
                        OutlinedButton(onClick = onRecord, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.editor_record))
                        }
                    }
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.editor_recording_remove)) }
                }
                else -> {
                    Button(onClick = onRecord, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.editor_record))
                    }
                    TextButton(onClick = onImport) { Text(stringResource(R.string.editor_recording_import)) }
                }
            }
        }
    }
}

@Composable
private fun ImageSection(imagePath: String?, fallbackKey: String, onPick: () -> Unit, onRemove: () -> Unit) {
    Text(stringResource(R.string.editor_image), style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        PracticeArtwork(imagePath = imagePath, fallbackKey = fallbackKey, modifier = Modifier.size(96.dp))
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (imagePath == null) R.string.editor_image_pick else R.string.editor_image_change))
            }
            if (imagePath != null) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.editor_image_remove)) }
            } else {
                Text(
                    stringResource(R.string.editor_image_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Número con botones − / + y atajos; también se puede escribir. */
@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    display: String,
    onChange: (Int) -> Unit,
    steps: List<Int>,
    presets: List<Int>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val step = steps.first()
            OutlinedButton(onClick = { onChange(value - step) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("−") }
            var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.filter(Char::isDigit).take(4)
                    text.toIntOrNull()?.let(onChange)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            OutlinedButton(onClick = { onChange(value + step) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { n ->
                androidx.compose.material3.FilterChip(
                    selected = value == n,
                    onClick = { onChange(n) },
                    label = { Text(n.toString()) },
                )
            }
        }
    }
}

/** Velocidad de lectura en múltiplos de la normal (120 palabras por minuto = 1×). */
@Composable
fun SpeedSlider(paceWpm: Int, onChange: (Int) -> Unit, onChangeFinished: (() -> Unit)? = null, showHint: Boolean = false) {
    val factor = paceWpm / 120f
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.editor_speed), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.speed_value, "%.2f".format(factor).trimEnd('0').trimEnd('.', ',')),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = factor,
            onValueChange = { onChange(((it * 20).roundToInt() / 20f * 120).roundToInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = 0.5f..1.6f,
            steps = 21,
        )
        if (showHint) {
            Text(stringResource(R.string.speed_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
