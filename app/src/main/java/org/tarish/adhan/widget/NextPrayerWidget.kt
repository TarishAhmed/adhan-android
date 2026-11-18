package org.tarish.adhan.widget

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.tarish.adhan.MainActivity
import org.tarish.adhan.R
import org.tarish.adhan.alarm.NotificationChannels
import org.tarish.adhan.core.format.Remaining
import org.tarish.adhan.core.format.widgetRemaining
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.NextPrayerFinder
import org.tarish.adhan.ui.theme.DarkColors
import org.tarish.adhan.ui.theme.LightColors
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WidgetState(
    val prayerName: String,
    val time: String,
    val amPm: String?,
    val remaining: String,
    val city: String,
    val country: String,
    val isTomorrow: Boolean,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsStore
    fun prayerRepo(): PrayerTimesRepository
    fun clock(): Clock
}

/** §12 — next prayer widget. Never touches the network. */
class NextPrayerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = runCatching { loadState(context) }.getOrNull()
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                Content(context, state)
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetState? {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val loc = ep.settings().location.first() ?: return null
        val clock = ep.clock()
        val zone = loc.zone ?: clock.deviceZone()
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        val days = ep.prayerRepo().daysOnce(loc.key, today, today.plusDays(1))
        val next = NextPrayerFinder.nextSalah(now, days.firstOrNull { it.date == today }, days.firstOrNull { it.date == today.plusDays(1) })
            ?: return null
        val is24 = DateFormat.is24HourFormat(context)
        val zoned = next.time.atZone(zone)
        val time = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(zoned)
        val amPm = if (is24) null else DateTimeFormatter.ofPattern("a", Locale.getDefault()).format(zoned)
        val remaining = when (val r = widgetRemaining(next.time, now)) {
            Remaining.Now -> context.getString(R.string.widget_now)
            is Remaining.Minutes -> context.getString(R.string.widget_in_min, r.m)
            is Remaining.Hours -> context.getString(R.string.widget_in_h, r.h)
            is Remaining.HoursMinutes -> context.getString(R.string.widget_in_h_min, r.h, r.m)
        }
        return WidgetState(
            prayerName = context.getString(NotificationChannels.prayerNameRes(next.prayer)),
            time = time, amPm = amPm, remaining = remaining,
            city = loc.displayName, country = loc.country.orEmpty(), isTomorrow = next.isTomorrow,
        )
    }

    @Composable
    private fun Content(context: Context, s: WidgetState?) {
        val size = LocalSize.current
        val compact = size.width < 180.dp || size.height < 110.dp
        val radius = if (Build.VERSION.SDK_INT >= 31) android.R.dimen.system_app_widget_background_radius else null
        var mod = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
        mod = if (radius != null) mod.cornerRadius(radius) else mod.cornerRadius(16.dp)
        if (s != null) {
            val cd = context.getString(R.string.widget_cd, s.prayerName, listOfNotNull(s.time, s.amPm).joinToString(" "), s.remaining, s.city)
            mod = mod.semantics { contentDescription = cd }
        }

        Column(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
            if (s == null) {
                Text(context.getString(R.string.widget_placeholder),
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
                return@Column
            }
            if (compact) {
                Text(s.prayerName, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.time, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                    if (s.amPm != null && size.width >= 130.dp) {
                        Spacer(GlanceModifier.width(2.dp))
                        Text(s.amPm, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    }
                }
                Text(s.remaining, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.prayerName, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                    Image(ImageProvider(R.drawable.ic_logo), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.time, style = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                    if (s.amPm != null) {
                        Spacer(GlanceModifier.width(4.dp))
                        Text(s.amPm, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    }
                }
                Text(s.remaining, style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.primary))
                Text(s.city, maxLines = 1, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant))
                if (s.country.isNotEmpty()) {
                    Text(s.country, maxLines = 1, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
                }
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 110.dp)
    }
}
