package org.tarish.adhan.ui.update

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tarish.adhan.R
import org.tarish.adhan.data.UpdateDownload
import org.tarish.adhan.data.UpdaterState
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.util.openUpdateUrl

/** Starts the in-app update, or opens the release page when the release has no APK. */
@Composable
private fun rememberUpdateAction(state: UpdaterState, onUpdate: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    return { if (state.info.apkUrl.isBlank()) ctx.openUpdateUrl(state.info.pageUrl) else onUpdate() }
}

/** §8.9 — blocking screen; UI only, alarms keep working. */
@Composable
fun ForceUpdateScreen(state: UpdaterState, onUpdate: () -> Unit) {
    val action = rememberUpdateAction(state, onUpdate)
    BackHandler { /* consume */ }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radius.xl)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                stringResource(R.string.update_required_title), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.update_required_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.update_version, state.info.version), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.xxl))
            UpdateProgress(state)
            Button(
                onClick = action,
                enabled = state.download !is UpdateDownload.Downloading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Download, null)
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(buttonLabel(state)))
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.update_required_help), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun GeneralUpdateDialog(state: UpdaterState, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val action = rememberUpdateAction(state, onUpdate)
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val size = state.info.sizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(ctx, it) }
                Text(
                    if (size != null) stringResource(R.string.update_available_body_size, state.info.version, size)
                    else stringResource(R.string.update_available_body, state.info.version)
                )
                if (state.info.notes.isNotBlank()) {
                    Text(state.info.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                UpdateProgress(state)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) } },
        confirmButton = {
            TextButton(onClick = action, enabled = state.download !is UpdateDownload.Downloading) { Text(stringResource(buttonLabel(state))) }
        },
    )
}

@Composable
private fun UpdateProgress(state: UpdaterState) {
    when (val d = state.download) {
        is UpdateDownload.Downloading -> {
            if (d.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.md))
        }
        UpdateDownload.Failed -> {
            Text(stringResource(R.string.update_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.md))
        }
        UpdateDownload.Idle, UpdateDownload.Ready -> Unit
    }
}

fun buttonLabel(state: UpdaterState): Int = when (state.download) {
    is UpdateDownload.Downloading -> R.string.update_downloading
    UpdateDownload.Ready -> R.string.update_install
    UpdateDownload.Failed -> R.string.action_try_again
    UpdateDownload.Idle -> R.string.action_update
}
