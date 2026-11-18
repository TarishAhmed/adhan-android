package org.tarish.adhan.ui.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.tarish.adhan.alarm.NotificationChannels
import org.tarish.adhan.core.format.formatClock
import org.tarish.adhan.domain.model.PrayerName
import java.time.Instant
import java.time.ZoneId

@Composable
fun rememberIs24h(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { DateFormat.is24HourFormat(ctx) }
}

@Composable
fun clockText(instant: Instant, zone: ZoneId): String = formatClock(instant, zone, rememberIs24h())

@Composable
fun prayerName(p: PrayerName): String = stringResource(NotificationChannels.prayerNameRes(p))

/** "Europe/London" → "London" (D-06). */
fun zoneCity(zone: ZoneId): String = zone.id.substringAfterLast('/').replace('_', ' ')
