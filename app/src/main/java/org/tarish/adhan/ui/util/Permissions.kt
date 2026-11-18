package org.tarish.adhan.ui.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import org.tarish.adhan.R

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

class LocationPermissionRequester(val request: () -> Unit)

/**
 * Requests location with the §9.4 rationale dialog. [onResult] gets (granted, permanentlyDenied).
 * When already permanently denied, the request opens app settings instead.
 */
@Composable
fun rememberLocationPermissionRequester(
    onChooseCity: () -> Unit,
    onResult: (granted: Boolean, permanentlyDenied: Boolean) -> Unit,
): LocationPermissionRequester {
    val ctx = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val granted = res.values.any { it }
        val activity = ctx.findActivity()
        val permanently = !granted && activity != null &&
            LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        onResult(granted, permanently)
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            text = { Text(stringResource(R.string.location_rationale)) },
            confirmButton = {
                TextButton(onClick = { showRationale = false; launcher.launch(LOCATION_PERMISSIONS) }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; onChooseCity() }) { Text(stringResource(R.string.action_choose_city)) }
            },
        )
    }
    return remember(launcher) {
        LocationPermissionRequester {
            val activity = ctx.findActivity()
            val rationale = activity != null && LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (rationale) showRationale = true else launcher.launch(LOCATION_PERMISSIONS)
        }
    }
}
