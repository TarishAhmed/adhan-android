package org.tarish.adhan.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/** Emits immediately, then every [periodMs] aligned to the period boundary (e.g. whole seconds / minutes). */
fun tickerFlow(periodMs: Long, clock: Clock = SystemClock): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        val ms = now.toEpochMilli()
        delay(periodMs - (ms % periodMs))
    }
}
