package ar.param.advisor.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.param.advisor.R
import ar.param.advisor.ui.navigation.ParamitaLinks

/** Las 5 preguntas aprobadas, con el texto exacto de la spec de ética §4.2. */
private val FAQ = listOf(
    R.string.faq_q1 to R.string.faq_a1,
    R.string.faq_q2 to R.string.faq_a2,
    R.string.faq_q3 to R.string.faq_a3,
    R.string.faq_q4 to R.string.faq_a4,
    R.string.faq_q5 to R.string.faq_a5,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.faq_title)) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FAQ.forEach { (q, a) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(q), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(a), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                stringResource(R.string.about_independent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_content_review),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onOpenLink(ParamitaLinks.HOME) }) { Text(stringResource(R.string.link_home)) }
        }
    }
}
