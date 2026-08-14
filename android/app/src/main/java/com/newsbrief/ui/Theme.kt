package com.newsbrief.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4965),
    onPrimary = Color.White,
    secondary = Color(0xFF5FA8D3),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7E8),
    onPrimary = Color(0xFF0B2A3B),
    secondary = Color(0xFF5FA8D3),
    background = Color(0xFF121417),
    surface = Color(0xFF1C1F24),
)

/** 상승 빨강 / 하락 파랑 — 국내 증시 관행을 따른다. */
val RiseColor = Color(0xFFD32F2F)
val FallColor = Color(0xFF1976D2)

@Composable
fun NewsBriefTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
