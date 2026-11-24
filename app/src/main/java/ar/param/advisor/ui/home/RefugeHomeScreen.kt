package ar.param.advisor.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.param.advisor.R
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.local.entity.PracticeWithDetails
import ar.param.advisor.engine.RunnerSnapshot
import ar.param.advisor.engine.RunnerStatus
import ar.param.advisor.ui.RefugeHomeUiState
import ar.param.advisor.ui.navigation.ParamitaLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefugeHomeScreen(
    state: RefugeHomeUiState,
    onOpenPractice: (String) -> Unit,
    onOpenFaq: () -> Unit,
    onStartWithRefugeChange: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall) },
                actions = { TextButton(onClick = onOpenFaq) { Text(stringResource(R.string.home_faq)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val snapshot = state.activeRunnerSnapshot
            if (snapshot.isActive && snapshot.practiceId != null) {
                item { ActiveSessionBanner(snapshot) { onOpenPractice(snapshot.practiceId) } }
            }
            if (state.isLoading) {
                item {
                    Text(
                        stringResource(R.string.home_preparing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }
            state.impulsePractice?.let { impulse ->
                item { ImpulseCard { onOpenPractice(impulse.practice.id) } }
            }
            state.featuredPractice?.let { featured ->
                item { SectionTitle(R.string.home_featured) }
                item { PracticeCard(featured, highlighted = true) { onOpenPractice(featured.practice.id) } }
            }
            item { SectionTitle(R.string.home_all_practices) }
            items(state.practices, key = { it.practice.id }) { p ->
                PracticeCard(p, highlighted = false) { onOpenPractice(p.practice.id) }
            }
            item { WeekNote(state.practiceMomentsThisWeek) }
            item { StartWithRefugeRow(state.startWithRefuge, onStartWithRefugeChange) }
            item { ParamitaLinksCard(onOpenLink) }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ActiveSessionBanner(snapshot: RunnerSnapshot, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (snapshot.status == RunnerStatus.PAUSED) R.string.banner_paused else R.string.banner_playing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                snapshot.practiceTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (snapshot.mantraTargetRepetitions > 0) {
                Text(
                    stringResource(R.string.progress_round, snapshot.mantraRepetition, snapshot.mantraTargetRepetitions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ImpulseCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.impulse_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                stringResource(R.string.impulse_card_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@StringRes
fun practiceTypeLabel(type: String): Int = when (type) {
    PracticeTypes.MANTRA -> R.string.type_mantra
    PracticeTypes.GUIDED_MEDITATION -> R.string.type_meditation
    PracticeTypes.IMPULSE_PAUSE -> R.string.type_pause
    else -> R.string.type_prayer
}

@Composable
private fun PracticeCard(p: PracticeWithDetails, highlighted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_lotus_calm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.practice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                p.practice.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(
                        R.string.practice_meta,
                        stringResource(practiceTypeLabel(p.practice.type)),
                        p.practice.estimatedMinutes,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.practice.isFavorite) {
                Text("★", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun WeekNote(moments: Int) {
    val text = if (moments > 0) {
        androidx.compose.ui.res.pluralStringResource(R.plurals.week_moments, moments, moments)
    } else {
        stringResource(R.string.week_welcome)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StartWithRefugeRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!enabled) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_start_refuge), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.setting_start_refuge_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun ParamitaLinksCard(onOpenLink: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.links_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.links_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onOpenLink(ParamitaLinks.MEDITA_CON_NOSOTROS) }) { Text(stringResource(R.string.link_meditate)) }
            TextButton(onClick = { onOpenLink(ParamitaLinks.CURSOS) }) { Text(stringResource(R.string.link_courses)) }
            TextButton(onClick = { onOpenLink(ParamitaLinks.HOME) }) { Text(stringResource(R.string.link_home)) }
        }
    }
}
