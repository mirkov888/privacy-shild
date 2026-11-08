package com.mora.shield

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD9A6),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF00522F),
    onPrimaryContainer = Color(0xFF9BF6C1),
    secondary = Color(0xFFB3CCBB),
    tertiary = Color(0xFFA3CDDC),
    background = Color(0xFF0B100E),
    surface = Color(0xFF111915),
    surfaceVariant = Color(0xFF202B25),
    surfaceTint = Color(0xFF7FD9A6),
    outline = Color(0xFF46534B),
    outlineVariant = Color(0xFF303C34),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9BF6C1),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4D6355),
    tertiary = Color(0xFF3A6573),
    background = Color(0xFFF4F8F4),
    surface = Color(0xFFFAFCF9),
    surfaceVariant = Color(0xFFE7EFE8),
    surfaceTint = Color(0xFF006D3F),
    outline = Color(0xFF737F75),
    outlineVariant = Color(0xFFD0DAD1),
    error = Color(0xFFBA1A1A)
)

@Composable
fun MoraTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}
