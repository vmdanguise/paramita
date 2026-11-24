package ar.param.advisor.ui.wellbeing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ar.param.advisor.R
import ar.param.advisor.data.local.entity.MindfulAppTargetEntity
import ar.param.advisor.digitalwellbeing.AppCategories
import ar.param.advisor.digitalwellbeing.SUGGESTED_TARGETS
import ar.param.advisor.digitalwellbeing.isMindfulAccessibilityServiceEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val suggestedCategory: String?)

/** Apps que se pueden abrir desde el launcher (REQ-032/034: `<queries>`, sin QUERY_ALL_PACKAGES). */
fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), SUGGESTED_TARGETS[it.packageName]) }
        .sortedWith(compareBy<InstalledApp> { it.suggestedCategory == null }.thenBy { it.label.lowercase() })
}

fun categoryLabel(category: String): Int = when (category) {
    AppCategories.SHOPPING -> R.string.category_shopping
    AppCategories.SOCIAL_SCROLL -> R.string.category_social
    else -> R.string.category_other
}

private val COOLDOWNS = listOf(1, 5, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulAppTargetsScreen(
    targets: List<MindfulAppTargetEntity>,
    onSave: (MindfulAppTargetEntity) -> Unit,
    onDelete: (String) -> Unit,
    onTryPause: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // REQ-030: se reevalúa al volver de Ajustes.
    val serviceEnabled by produceState(isMindfulAccessibilityServiceEnabled(context), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { value = isMindfulAccessibilityServiceEnabled(context) }
    }
    val installed by produceState(emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    val byPackage = remember(targets) { targets.associateBy { it.packageName } }
    val installedPackages = remember(installed) { installed.map { it.packageName }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.targets_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ServiceStatusCard(serviceEnabled, onActivate = { showDisclosure = true }, onTryPause = onTryPause) }
            item {
                Text(
                    stringResource(R.string.targets_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val orphanTargets = targets.filter { it.packageName !in installedPackages && installed.isNotEmpty() }
            items(orphanTargets, key = { "orphan_" + it.packageName }) { t ->
                TargetRow(
                    label = stringResource(R.string.targets_not_installed, t.displayName),
                    target = t,
                    suggestedCategory = null,
                    onToggle = { if (!it) onDelete(t.packageName) },
                    onSave = onSave,
                )
            }
            item {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.targets_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (installed.isEmpty()) {
                item { Text(stringResource(R.string.targets_loading), style = MaterialTheme.typography.bodyMedium) }
            }
            val visible = installed.filter { filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true) }
            items(visible, key = { it.packageName }) { app ->
                TargetRow(
                    label = app.label,
                    target = byPackage[app.packageName],
                    suggestedCategory = app.suggestedCategory,
                    onToggle = { on ->
                        val existing = byPackage[app.packageName]
                        when {
                            on && existing == null -> onSave(
                                MindfulAppTargetEntity(
                                    packageName = app.packageName,
                                    displayName = app.label,
                                    category = app.suggestedCategory ?: AppCategories.OTHER,
                                    promptEnabled = true,
                                    cooldownMinutes = 15,
                                ),
                            )
                            existing != null -> onSave(existing.copy(promptEnabled = on))
                        }
                    },
                    onSave = onSave,
                )
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(R.string.disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    openAccessibilitySettings(context)
                }) { Text(stringResource(R.string.disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.disclosure_not_now)) }
            },
        )
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ServiceStatusCard(enabled: Boolean, onActivate: () -> Unit, onTryPause: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (enabled) R.string.service_active else R.string.service_pending),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (enabled) R.string.service_active_body else R.string.service_pending_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!enabled) {
                Button(onClick = onActivate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.service_activate))
                }
            }
            TextButton(onClick = onTryPause) { Text(stringResource(R.string.service_try)) }
        }
    }
}

@Composable
private fun TargetRow(
    label: String,
    target: MindfulAppTargetEntity?,
    suggestedCategory: String?,
    onToggle: (Boolean) -> Unit,
    onSave: (MindfulAppTargetEntity) -> Unit,
) {
    val on = target?.promptEnabled == true
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onToggle(!on) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    val category = target?.category ?: suggestedCategory
                    if (category != null) {
                        Text(
                            stringResource(categoryLabel(category)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = on, onCheckedChange = null)
            }
            if (target != null && target.promptEnabled) {
                Text(stringResource(R.string.targets_cooldown), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COOLDOWNS.forEach { m ->
                        FilterChip(
                            selected = target.cooldownMinutes == m,
                            onClick = { onSave(target.copy(cooldownMinutes = m)) },
                            label = { Text(stringResource(R.string.targets_minutes, m)) },
                        )
                    }
                }
            }
        }
    }
}
