package ar.param.advisor.ui.wellbeing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.param.advisor.R
import ar.param.advisor.digitalwellbeing.IMPULSE_DEDICATION
import ar.param.advisor.digitalwellbeing.ImpulseEmotion
import ar.param.advisor.digitalwellbeing.ImpulsePauseDecision
import ar.param.advisor.digitalwellbeing.PauseOutcome
import ar.param.advisor.digitalwellbeing.suggestedPracticeFor
import kotlinx.coroutines.delay

private enum class Stage { BREATH, INQUIRY, AMOUNT, REFLECT }

fun emotionLabel(emotion: ImpulseEmotion): Int = when (emotion) {
    ImpulseEmotion.ANXIETY -> R.string.emotion_anxiety
    ImpulseEmotion.TIREDNESS -> R.string.emotion_tiredness
    ImpulseEmotion.BOREDOM -> R.string.emotion_boredom
    ImpulseEmotion.IMPULSE_BUY -> R.string.emotion_impulse_buy
}

private fun reflectionFor(emotion: ImpulseEmotion): Int = when (emotion) {
    ImpulseEmotion.ANXIETY -> R.string.reflect_anxiety
    ImpulseEmotion.TIREDNESS -> R.string.reflect_tiredness
    ImpulseEmotion.BOREDOM -> R.string.reflect_boredom
    ImpulseEmotion.IMPULSE_BUY -> R.string.reflect_impulse_buy
}

/**
 * Pausa lúcida en 4 pasos (spec de bienestar REQ-001). "Continuar con consciencia"
 * está visible en todos los pasos y nunca pide confirmación (SEC-001 de ética).
 */
@Composable
fun ImpulsePauseScreen(
    appName: String?,
    isManual: Boolean,
    packageName: String?,
    onRecord: (ImpulsePauseDecision) -> Unit,
    onRestInCalm: () -> Unit,
    onContinue: () -> Unit,
    onListen: (practiceId: String, targetReps: Int?) -> Unit,
    onCloseManual: () -> Unit,
) {
    var stage by rememberSaveable { mutableStateOf(Stage.BREATH) }
    var emotion by rememberSaveable { mutableStateOf<ImpulseEmotion?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }

    val context = appName ?: stringResource(R.string.pause_manual_context)
    fun decision(outcome: PauseOutcome) = emotion?.let {
        ImpulsePauseDecision(
            packageName = packageName,
            displayContext = context,
            emotion = it,
            outcome = outcome,
            avoidedAmountArs = amountText.replace(".", "").replace(",", ".").toDoubleOrNull(),
        )
    }
    val continueConsciously = {
        decision(PauseOutcome.PROCEEDED_CONSCIOUSLY)?.let(onRecord)
        onContinue()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                if (isManual) stringResource(R.string.pause_title_manual) else stringResource(R.string.pause_title_app, context),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            AnimatedContent(
                targetState = stage,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
                label = "etapa",
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (current) {
                        Stage.BREATH -> BreathStage(onDone = { stage = Stage.INQUIRY })
                        Stage.INQUIRY -> InquiryStage(isManual) { picked ->
                            emotion = picked
                            stage = if (picked == ImpulseEmotion.IMPULSE_BUY) Stage.AMOUNT else Stage.REFLECT
                        }
                        Stage.AMOUNT -> AmountStage(amountText, { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }) {
                            stage = Stage.REFLECT
                        }
                        Stage.REFLECT -> emotion?.let { e ->
                            ReflectStage(
                                emotion = e,
                                onListen = {
                                    decision(PauseOutcome.PRACTICED_MAITRI)?.let(onRecord)
                                    onListen(suggestedPracticeFor(e), null)
                                },
                                onRest = {
                                    decision(PauseOutcome.PAUSED_AND_LEFT_APP)?.let(onRecord)
                                    onRestInCalm()
                                },
                            )
                        }
                    }
                }
            }

            OutlinedButton(onClick = continueConsciously, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(if (isManual) R.string.pause_close else R.string.pause_continue))
            }
            if (!isManual) {
                TextButton(onClick = onCloseManual, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_close))
                }
            }
        }
    }
}

@Composable
private fun BreathStage(onDone: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(6) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000L)
            remaining--
        }
        onDone()
    }
    // Inhalación de 3 s y exhalación de 3 s.
    val inhaling = remaining > 3
    val scale by animateFloatAsState(if (inhaling) 1.25f else 0.85f, tween(2_800), label = "respiración")
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_lotus_calm),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp).scale(scale),
        )
    }
    Text(
        stringResource(R.string.pause_breath, remaining),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pause_skip)) }
}

@Composable
private fun InquiryStage(isManual: Boolean, onPick: (ImpulseEmotion) -> Unit) {
    Text(stringResource(R.string.pause_inquiry), style = MaterialTheme.typography.titleLarge)
    if (!isManual) {
        Text(
            stringResource(R.string.pause_inquiry_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ImpulseEmotion.entries.forEach { e ->
        OutlinedButton(onClick = { onPick(e) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(emotionLabel(e)))
        }
    }
}

@Composable
private fun AmountStage(value: String, onChange: (String) -> Unit, onNext: () -> Unit) {
    Text(stringResource(R.string.pause_need_question), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.pause_need_body), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.pause_amount_label)) },
        supportingText = { Text(stringResource(R.string.pause_amount_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.pause_next))
    }
}

@Composable
private fun ReflectStage(emotion: ImpulseEmotion, onListen: () -> Unit, onRest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            stringResource(reflectionFor(emotion)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(18.dp),
        )
    }
    Button(onClick = onListen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.pause_listen))
    }
    Button(onClick = onRest, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.pause_rest))
    }
    Text(
        IMPULSE_DEDICATION,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
