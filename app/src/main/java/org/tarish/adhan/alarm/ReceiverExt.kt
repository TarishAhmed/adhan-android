package org.tarish.adhan.alarm

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** goAsync() + coroutine with a 9-second budget (§3.2). Never throws. */
fun BroadcastReceiver.goAsync(scope: CoroutineScope, tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeoutOrNull(9_000) { block() } ?: Timber.w("%s timed out", tag)
        } catch (e: Exception) {
            Timber.e(e, "%s failed", tag)
        } finally {
            pending.finish()
        }
    }
}
