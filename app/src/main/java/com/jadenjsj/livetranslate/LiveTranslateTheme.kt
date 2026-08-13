package com.jadenjsj.livetranslate

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Material Theme Builder-style fallback schemes. Android 12+ uses the user's
// dynamic scheme; every custom role below has an explicit matching on-color.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CDBFF),
    onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFB9EAFF),
    secondary = Color(0xFFB5C9D5),
    onSecondary = Color(0xFF20333C),
    secondaryContainer = Color(0xFF374A53),
    onSecondaryContainer = Color(0xFFD1E5EF),
    tertiary = Color(0xFFC9C3EA),
    onTertiary = Color(0xFF312E4D),
    tertiaryContainer = Color(0xFF484565),
    onTertiaryContainer = Color(0xFFE6DFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C1117),
    onBackground = Color(0xFFDCE4EA),
    surface = Color(0xFF0C1117),
    onSurface = Color(0xFFDCE4EA),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFC0C8CC),
    surfaceContainer = Color(0xFF151B24),
    surfaceContainerHigh = Color(0xFF202732),
    outline = Color(0xFF8A9296),
    outlineVariant = Color(0xFF40484C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006781),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EAFF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF4D616C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E6F1),
    onSecondaryContainer = Color(0xFF091E27),
    tertiary = Color(0xFF5F5B7E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5DFFF),
    onTertiaryContainer = Color(0xFF1B1737),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFD),
    onBackground = Color(0xFF181C1F),
    surface = Color(0xFFF6FAFD),
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    surfaceContainer = Color(0xFFEAF0F4),
    surfaceContainerHigh = Color(0xFFDFE8ED),
    outline = Color(0xFF70787C),
    outlineVariant = Color(0xFFC0C8CC),
)

@Composable
fun LiveTranslateTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
