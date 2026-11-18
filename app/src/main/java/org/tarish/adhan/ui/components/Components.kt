package org.tarish.adhan.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StateView(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    primary: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (primary != null && onPrimary != null) {
            Spacer(Modifier.height(Spacing.xs))
            Button(onClick = onPrimary) { Text(primary) }
        }
        if (secondary != null && onSecondary != null) {
            OutlinedButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

/** Small uppercase label above a group or a value ("NEXT PRAYER", "PRAYER"). */
@Composable
fun EyebrowLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(),
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.W600),
        color = color,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    EyebrowLabel(text, modifier.padding(start = Spacing.lg, top = Spacing.xl, end = Spacing.lg, bottom = Spacing.sm))
}

/** The one card style: tonal surface, large radius, optional click. */
@Composable
fun AdhanCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xl)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, content = inner)
    else Surface(modifier = modifier, shape = shape, color = color, content = inner)
}

/**
 * Settings list row. [trailing] defaults to a chevron when [onClick] is set; pass a Switch etc. to
 * override. With a [checked] value the whole row toggles and reads as a switch to TalkBack.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowModifier = when {
        checked != null && onCheckedChange != null ->
            Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.primary) } },
        trailingContent = when {
            trailing != null -> trailing
            checked != null -> { { Switch(checked = checked, onCheckedChange = null) } }
            onClick != null -> { { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) } }
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.then(rowModifier).heightIn(min = 56.dp),
    )
}

/** Shimmer block: surfaceContainerHighest ↔ surfaceContainerHigh, alpha 0.6→1.0 over 1 s. */
@Composable
fun Skeleton(modifier: Modifier, corner: Dp = Radius.lg) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha")
    Box(
        modifier
            .graphicsLayer { alpha = a }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(corner))
    )
}
