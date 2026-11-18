package org.tarish.adhan.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.tarish.adhan.domain.model.ThemeMode
import org.tarish.adhan.ui.theme.AdhanTheme
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.theme.extendedColors
import org.tarish.adhan.ui.theme.tabular

/** Light, dark and 200% text for every shared component. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "200% text", showBackground = true, fontScale = 2f)
annotation class ComponentPreviews

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    AdhanTheme(ThemeMode.SYSTEM) { Surface(color = MaterialTheme.colorScheme.background, content = content) }
}

@ComponentPreviews
@Composable
private fun CardAndLabelPreview() = PreviewSurface {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Prayer")
        AdhanCard(onClick = {}) {
            EyebrowLabel("Up next")
            Text("Maghrib", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@ComponentPreviews
@Composable
private fun SettingRowPreview() = PreviewSurface {
    Column {
        SettingRow("Notifications", icon = Icons.Outlined.Notifications, subtitle = "5 of 5 prayers have adhan on", onClick = {})
        SettingRow("Auto-detect location", icon = Icons.Outlined.LocationOn, checked = true, onCheckedChange = {})
    }
}

@ComponentPreviews
@Composable
private fun OrnamentPreview() = PreviewSurface {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("1", "9", "114").forEach { StarBadge(it, MaterialTheme.typography.labelMedium.tabular()) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .geometricPattern(extendedColors.ornament, alpha = 0.14f)
        )
    }
}
