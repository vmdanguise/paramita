package ar.param.advisor.ui.voice

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.param.advisor.R
import ar.param.advisor.engine.RecitationMode
import ar.param.advisor.engine.SpeechAndBellController
import ar.param.advisor.engine.VoiceOption
import ar.param.advisor.engine.VoicePreset
import ar.param.advisor.engine.VoiceSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@StringRes
private fun presetLabel(p: VoicePreset): Int = when (p) {
    VoicePreset.NATURAL -> R.string.voice_preset_natural
    VoicePreset.WARM -> R.string.voice_preset_warm
    VoicePreset.CLEAR -> R.string.voice_preset_clear
    VoicePreset.SOFT -> R.string.voice_preset_soft
    VoicePreset.DEEP -> R.string.voice_preset_deep
}

/** Elegir la voz, el tono, el volumen y la ecualización con que se leen las prácticas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    settings: VoiceSettings,
    onChange: (VoiceSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Un motor propio solo para probar: no interfiere con la práctica que pueda estar sonando.
    val tester = remember { SpeechAndBellController(context, settings) }
    DisposableEffect(Unit) { onDispose { tester.release() } }
    val voices by produceState<List<VoiceOption>?>(initialValue = null) { value = tester.availableVoices() }
    var testJob by remember { mutableStateOf<Job?>(null) }
    val testPhrase = stringResource(R.string.voice_test_phrase)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.voice_intro), style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = {
                    testJob?.cancel()
                    tester.stopSpeaking()
                    tester.voice = settings
                    testJob = scope.launch { tester.speakAndAwait(testPhrase, RecitationMode.SPOKEN) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.voice_test)) }

            HorizontalDivider()
            Text(stringResource(R.string.voice_choose), style = MaterialTheme.typography.titleSmall)
            VoiceRow(stringResource(R.string.voice_default), selected = settings.voiceName == null) {
                onChange(settings.copy(voiceName = null))
            }
            voices?.let { list ->
                list.forEachIndexed { i, v ->
                    VoiceRow(
                        label = "${v.locale.getDisplayName(java.util.Locale.forLanguageTag("es"))} · ${i + 1}",
                        selected = settings.voiceName == v.name,
                    ) { onChange(settings.copy(voiceName = v.name)) }
                }
                if (list.isEmpty()) {
                    Text(stringResource(R.string.voice_none_found), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = {
                try {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }) { Text(stringResource(R.string.voice_install)) }

            HorizontalDivider()
            LabeledSlider(
                label = stringResource(R.string.voice_pitch),
                display = stringResource(R.string.voice_percent, (settings.pitch * 100).roundToInt()),
                value = settings.pitch,
                range = VoiceSettings.MIN_PITCH..VoiceSettings.MAX_PITCH,
                steps = 17,
                onChange = { onChange(settings.copy(pitch = (it * 20).roundToInt() / 20f)) },
            )
            LabeledSlider(
                label = stringResource(R.string.voice_volume),
                display = stringResource(R.string.voice_percent, (settings.volume * 100).roundToInt()),
                value = settings.volume,
                range = VoiceSettings.MIN_VOLUME..1f,
                steps = 15,
                onChange = { onChange(settings.copy(volume = (it * 20).roundToInt() / 20f)) },
            )

            HorizontalDivider()
            Text(stringResource(R.string.voice_eq), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoicePreset.entries.forEach { p ->
                    val active = abs(settings.bassDb - p.bassDb) < 0.5f && abs(settings.midDb - p.midDb) < 0.5f && abs(settings.trebleDb - p.trebleDb) < 0.5f
                    FilterChip(
                        selected = active,
                        onClick = { onChange(settings.copy(bassDb = p.bassDb, midDb = p.midDb, trebleDb = p.trebleDb)) },
                        label = { Text(stringResource(presetLabel(p))) },
                    )
                }
            }
            EqSlider(stringResource(R.string.voice_eq_bass), settings.bassDb) { onChange(settings.copy(bassDb = it)) }
            EqSlider(stringResource(R.string.voice_eq_mid), settings.midDb) { onChange(settings.copy(midDb = it)) }
            EqSlider(stringResource(R.string.voice_eq_treble), settings.trebleDb) { onChange(settings.copy(trebleDb = it)) }

            TextButton(onClick = { onChange(VoiceSettings()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.voice_reset))
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    display: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun EqSlider(label: String, db: Float, onChange: (Float) -> Unit) {
    val shown = db.roundToInt()
    LabeledSlider(
        label = label,
        display = stringResource(R.string.voice_db, if (shown > 0) "+$shown" else shown.toString()),
        value = db,
        range = -VoiceSettings.MAX_GAIN_DB..VoiceSettings.MAX_GAIN_DB,
        steps = 23,
        onChange = { onChange(it.roundToInt().toFloat()) },
    )
}

@Suppress("unused")
private fun Modifier.clickableRow(onClick: () -> Unit) = clickable(onClick = onClick)
