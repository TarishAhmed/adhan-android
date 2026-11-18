package org.tarish.adhan.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.tarish.adhan.R
import org.tarish.adhan.ui.util.BatteryIntents
import org.tarish.adhan.ui.util.isIgnoringBatteryOptimizations
import org.tarish.adhan.ui.util.tryStart

/** §8.10.3 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatterySheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var ignoring by remember { mutableStateOf(ctx.isIgnoringBatteryOptimizations()) }
    LifecycleResumeEffect(Unit) {
        ignoring = ctx.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.battery_body), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.battery_step1), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.battery_step2), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.battery_step3), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (ignoring) R.string.battery_status_off else R.string.battery_status_on),
                style = MaterialTheme.typography.labelLarge,
                color = if (ignoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            SheetButton(stringResource(R.string.battery_turn_off)) { BatteryIntents.requestIgnore(ctx) }
            SheetButton(stringResource(R.string.battery_open_settings)) { BatteryIntents.openSettings(ctx) }
            BatteryIntents.oemShortcuts(ctx).forEach { s ->
                SheetButton(stringResource(s.labelRes)) { ctx.tryStart(*s.intents.toTypedArray()) }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
