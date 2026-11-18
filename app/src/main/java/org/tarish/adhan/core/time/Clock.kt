package org.tarish.adhan.core.time

import java.time.Instant
import java.time.ZoneId

/** Injected source of "now" so every time-dependent component can be tested with a fixed clock. */
interface Clock {
    fun now(): Instant
    fun deviceZone(): ZoneId
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun deviceZone(): ZoneId = ZoneId.systemDefault()
}

class FixedClock(var instant: Instant, private val zone: ZoneId = ZoneId.of("UTC")) : Clock {
    override fun now(): Instant = instant
    override fun deviceZone(): ZoneId = zone
}
