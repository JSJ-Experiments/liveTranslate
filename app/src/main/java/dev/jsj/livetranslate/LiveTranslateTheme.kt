package dev.jsj.livetranslate

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

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CDBFF),
    onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFB9EAFF),
    secondary = Color(0xFFB5C9D5),
    background = Color(0xFF0C1117),
    surface = Color(0xFF0C1117),
    surfaceContainer = Color(0xFF151B24),
    surfaceContainerHigh = Color(0xFF202732),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006781),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EAFF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF4D616C),
    background = Color(0xFFF6FAFD),
    surface = Color(0xFFF6FAFD),
    surfaceContainer = Color(0xFFEAF0F4),
    surfaceContainerHigh = Color(0xFFDFE8ED),
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
