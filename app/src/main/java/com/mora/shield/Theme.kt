package com.mora.shield

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
    primary = Color(0xFF7FD9A6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = Color(0xFF9BF6C1),
    secondary = Color(0xFFB3CCBB),
    tertiary = Color(0xFFA3CDDC),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceVariant = Color(0xFF3F4943),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9BF6C1),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4D6355),
    tertiary = Color(0xFF3A6573),
    background = Color(0xFFF5FBF5),
    surface = Color(0xFFF5FBF5),
    surfaceVariant = Color(0xFFDBE5DC),
    error = Color(0xFFBA1A1A)
)

@Composable
fun MoraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
