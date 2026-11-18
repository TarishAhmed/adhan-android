package org.tarish.adhan

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.tarish.adhan.widget.NextPrayerWidget

/** Keeps the legacy Flutter provider class name so placed widgets survive the update (§6.4 step 7). */
class AdhanHomeWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}
