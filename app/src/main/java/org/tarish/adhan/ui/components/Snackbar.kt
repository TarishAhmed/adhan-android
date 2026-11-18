package org.tarish.adhan.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** App-wide snackbar host so a message survives navigation (e.g. "Location set to …"). */
val LocalSnackbarHost = staticCompositionLocalOf { SnackbarHostState() }

/** Shows on the root scope, so it outlives the screen that triggered it. */
class AppSnackbar(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }
}

val LocalAppSnackbar = staticCompositionLocalOf { AppSnackbar(SnackbarHostState(), MainScope()) }
