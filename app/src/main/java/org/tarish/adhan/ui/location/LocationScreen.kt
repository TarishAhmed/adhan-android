package org.tarish.adhan.ui.location

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.tarish.adhan.R
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.components.EyebrowLabel
import org.tarish.adhan.ui.components.AdhanCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.domain.model.StoredLocation
import org.tarish.adhan.ui.components.LocalAppSnackbar
import org.tarish.adhan.ui.components.LocalSnackbarHost
import org.tarish.adhan.ui.components.SectionHeader
import org.tarish.adhan.ui.components.StateView
import org.tarish.adhan.ui.theme.NotoSerif
import org.tarish.adhan.ui.theme.tabular
import org.tarish.adhan.ui.util.appDetailsIntent
import org.tarish.adhan.ui.util.messageRes
import org.tarish.adhan.ui.util.rememberIs24h
import org.tarish.adhan.ui.util.rememberLocationPermissionRequester
import org.tarish.adhan.ui.util.tryStart
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(onBack: () -> Unit, vm: LocationViewModel = hiltViewModel()) {
    val loc by vm.location.collectAsStateWithLifecycle()
    val auto by vm.autoLocation.collectAsStateWithLifecycle()
    val busy by vm.gpsBusy.collectAsStateWithLifecycle()
    val gpsError by vm.gpsError.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current

    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) vm.useCurrentLocation()
    }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLocationPermissionRequester(onChooseCity = {}) { granted, permanently ->
        if (granted) vm.useCurrentLocation()
        else if (permanently) blocked = true
    }
    // Explain before leaving the app, so the system settings page isn't a surprise.
    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            icon = { Icon(Icons.Outlined.LocationOn, null) },
            title = { Text(stringResource(R.string.location_blocked_title)) },
            text = { Text(stringResource(R.string.location_blocked_body)) },
            dismissButton = { TextButton(onClick = { blocked = false }) { Text(stringResource(R.string.action_not_now)) } },
            confirmButton = {
                TextButton(onClick = { blocked = false; ctx.tryStart(ctx.appDetailsIntent()) }) { Text(stringResource(R.string.action_open_settings)) }
            },
        )
    }

    val appSnackbar = LocalAppSnackbar.current
    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            when (e) {
                is LocationEvent.Selected -> {
                    appSnackbar.show(ctx.getString(R.string.location_set_to, e.name))
                    onBack()
                }
                is LocationEvent.Error -> when (e.error) {
                    AppError.LocationServicesOff -> vm.settingsResolution()?.let { resolution.launch(IntentSenderRequest.Builder(it).build()) }
                    AppError.LocationPermissionDenied -> permission.request()
                    else -> Unit
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.location_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CurrentCard(loc, busy, gpsError) }
            item {
                FilledTonalButton(
                    onClick = { vm.useCurrentLocation() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.location_use_current))
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.location_auto)) },
                    supportingContent = { Text(stringResource(if (auto) R.string.location_auto_on else R.string.location_auto_off)) },
                    trailingContent = { Switch(checked = auto, onCheckedChange = vm::setAuto) },
                )
            }
            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.location_search)) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    enabled = vm.geocoderAvailable,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        when {
                            search is SearchState.Searching -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            query.isNotEmpty() -> IconButton(onClick = { vm.query.value = "" }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.location_clear_search))
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.location_search_hint)) },
                    supportingText = if (!vm.geocoderAvailable) {{ Text(stringResource(R.string.error_geocoder_unavailable)) }} else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                )
            }
            when (val s = search) {
                is SearchState.Results -> {
                    if (s.items.isEmpty()) item {
                        Text(stringResource(R.string.location_search_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(s.items) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(listOfNotNull(p.region, p.country).joinToString(", ")) },
                            trailingContent = {
                                Text(String.format(Locale.US, "%.2f, %.2f", p.lat, p.lng), style = MaterialTheme.typography.labelSmall.tabular())
                            },
                            modifier = Modifier.clickable { focus.clearFocus(); vm.select(p) },
                        )
                    }
                }
                is SearchState.Failed -> item {
                    Text(
                        stringResource(if (s.error is AppError.GeocoderUnavailable) R.string.error_geocoder_unavailable else R.string.location_search_error),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }
            item { Spacer(Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun CurrentCard(loc: StoredLocation?, busy: Boolean, error: AppError?) {
    val cs = MaterialTheme.colorScheme
    AdhanCard(color = cs.primaryContainer, modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                Spacer(Modifier.width(Spacing.xs))
                EyebrowLabel(stringResource(R.string.location_current), color = cs.onPrimaryContainer)
            }
            Spacer(Modifier.size(Spacing.sm))
            when {
                loc == null && busy -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(60.dp)) }
                loc == null && error != null -> StateView(Icons.Outlined.LocationOn, stringResource(R.string.error_location_generic), stringResource(error.messageRes()))
                loc == null -> Text(stringResource(R.string.location_chip_placeholder), style = MaterialTheme.typography.headlineSmall, fontFamily = NotoSerif)
                else -> {
                    Text(loc.displayName, style = MaterialTheme.typography.headlineSmall, fontFamily = NotoSerif, color = cs.onPrimaryContainer)
                    val second = listOfNotNull(loc.region, loc.country).joinToString(", ")
                    if (second.isNotEmpty()) Text(second, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
                    Text(
                        stringResource(R.string.location_coords, String.format(Locale.US, "%.4f", loc.lat), String.format(Locale.US, "%.4f", loc.lng)),
                        style = MaterialTheme.typography.bodySmall.tabular(), color = cs.onPrimaryContainer,
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {}, label = {
                            Text(stringResource(if (loc.source == StoredLocation.Source.GPS) R.string.location_source_gps else R.string.location_source_manual))
                        })
                        Spacer(Modifier.width(8.dp))
                        if (loc.updatedAt.toEpochMilli() > 0) {
                            val is24 = rememberIs24h()
                            val fmt = DateTimeFormatter.ofPattern(if (is24) "MMM d, HH:mm" else "MMM d, h:mm a", Locale.getDefault())
                            Text(
                                stringResource(R.string.location_last_updated, fmt.format(loc.updatedAt.atZone(ZoneId.systemDefault()))),
                                style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
