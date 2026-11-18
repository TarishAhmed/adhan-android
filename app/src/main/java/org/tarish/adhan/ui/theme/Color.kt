package org.tarish.adhan.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Emerald + gold on ivory (light) / deep green-black (dark); dynamic colour is off. */
val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B51), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2D1), onPrimaryContainer = Color(0xFF00513B),
    secondary = Color(0xFF4C6359), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DB), onSecondaryContainer = Color(0xFF354B41),
    tertiary = Color(0xFF8A6420), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E2B8), onTertiaryContainer = Color(0xFF5A3F0A),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFBF8F1), onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFBF8F1), onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFDEE4DF), onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974), outlineVariant = Color(0xFFBFC9C2),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C322E), inverseOnSurface = Color(0xFFECF2ED), inversePrimary = Color(0xFF8BD6B6),
    surfaceTint = Color(0xFF1B6B51),
    surfaceDim = Color(0xFFDCD8CE), surfaceBright = Color(0xFFFBF8F1),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F1E7),
    surfaceContainer = Color(0xFFEFEBE0), surfaceContainerHigh = Color(0xFFE9E5DA),
    surfaceContainerHighest = Color(0xFFE3DFD4),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD6B6), onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513B), onPrimaryContainer = Color(0xFFA6F2D1),
    secondary = Color(0xFFB3CCBF), onSecondary = Color(0xFF1E352B),
    secondaryContainer = Color(0xFF354B41), onSecondaryContainer = Color(0xFFCEE9DB),
    tertiary = Color(0xFFE2C27A), onTertiary = Color(0xFF3F2D00),
    tertiaryContainer = Color(0xFF5A4410), onTertiaryContainer = Color(0xFFF6E2B8),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1714), onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0E1714), onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404944), onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF404944),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDEE4DF), inverseOnSurface = Color(0xFF2C322E), inversePrimary = Color(0xFF1B6B51),
    surfaceTint = Color(0xFF8BD6B6),
    surfaceDim = Color(0xFF0E1714), surfaceBright = Color(0xFF343B37),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

/** `kaaba`/`onKaaba` theme the Qibla marker; `ornament` tints the geometric pattern. */
@Immutable
data class ExtendedColors(
    val warning: Color,
    val qiblaAligned: Color,
    val qiblaFar: Color,
    val kaaba: Color,
    val onKaaba: Color,
    val ornament: Color,
)

val LightExtended = ExtendedColors(
    warning = Color(0xFFFB8C00), qiblaAligned = Color(0xFF2E7D32), qiblaFar = Color(0xFFC62828),
    kaaba = Color(0xFF1A1C18), onKaaba = Color(0xFFE2C27A), ornament = Color(0xFFB8893B),
)
val DarkExtended = ExtendedColors(
    warning = Color(0xFFFFB74D), qiblaAligned = Color(0xFF81C784), qiblaFar = Color(0xFFEF9A9A),
    kaaba = Color(0xFF000000), onKaaba = Color(0xFFE2C27A), ornament = Color(0xFFE2C27A),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }
