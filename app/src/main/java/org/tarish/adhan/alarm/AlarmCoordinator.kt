package org.tarish.adhan.alarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.tarish.adhan.core.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmCoordinator {
    /** Debounced (500 ms), app-scoped, idempotent (§3.3). */
    fun rescheduleAll(reason: String)
    /** Immediate, for receivers/workers that must finish before returning. */
    suspend fun rescheduleNow(reason: String)
}

@OptIn(FlowPreview::class)
@Singleton
class DefaultAlarmCoordinator @Inject constructor(
    private val scheduler: AlarmScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) : AlarmCoordinator {
    private val requests = MutableSharedFlow<String>(extraBufferCapacity = 16)

    init {
        scope.launch { requests.debounce(500).collect { scheduler.rescheduleAll(it) } }
    }

    override fun rescheduleAll(reason: String) { requests.tryEmit(reason) }

    override suspend fun rescheduleNow(reason: String) = scheduler.rescheduleAll(reason)
}
