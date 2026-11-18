package org.tarish.adhan.domain.model

import java.time.Instant

enum class Urgency { GREEN, YELLOW, RED, NEUTRAL }

/** Fractions 0..1 of the window. */
data class Segment(val start: Double, val end: Double, val urgency: Urgency)

data class PrayerWindow(
    val start: PrayerName,
    val startAt: Instant,
    val end: PrayerName,
    val endAt: Instant,
    val progress: Float,
    val urgency: Urgency,
    val segments: List<Segment>,
)
