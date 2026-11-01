package com.relkin.carstatus.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.relkin.carstatus.Prefs
import com.relkin.carstatus.data.CostCat

private val Light = lightColorScheme(
    primary = Color(0xFF1E5AA8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF), onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71), secondaryContainer = Color(0xFFD9E3F8), onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6E5676), tertiaryContainer = Color(0xFFF7D8FF),
    background = Color(0xFFF9F9FC), surface = Color(0xFFF9F9FC),
    surfaceContainer = Color(0xFFEDEDF4), surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerLow = Color(0xFFF3F3FA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA7C8FF), onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C), onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFBDC7DC), secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDABDE2), tertiaryContainer = Color(0xFF553F5D),
    background = Color(0xFF111318), surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerLow = Color(0xFF191C20),
)

/**
 * Цвета данных. Статусы (HP) — отдельная фиксированная палитра, всегда с подписью %;
 * категории расходов — первые пять категориальных слотов в постоянном порядке.
 */
data class DataColors(
    val good: Color, val warning: Color, val serious: Color, val critical: Color,
    val cats: List<Color>, val track: Color, val gap: Color,
) {
    fun cat(c: CostCat) = cats[c.ordinal]

    /** Цвет «здоровья» по доле 0..1. */
    fun hp(v: Double?): Color = when {
        v == null -> track
        v <= 0.2 -> critical
        v <= 0.4 -> serious
        v <= 0.6 -> warning
        else -> good
    }
}

val LocalData = staticCompositionLocalOf {
    DataColors(Color(0xFF0CA30C), Color(0xFFFAB219), Color(0xFFEC835A), Color(0xFFD03B3B), emptyList(), Color.Gray, Color.White)
}

@Composable
fun CarTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val theme by prefs.theme.state.collectAsState()
    val dynamic by prefs.dynamicColor.state.collectAsState()
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic == 1 && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val data = DataColors(
        good = Color(0xFF0CA30C), warning = Color(0xFFFAB219), serious = Color(0xFFEC835A), critical = Color(0xFFD03B3B),
        cats = if (dark) listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF9A6BDB), Color(0xFF199E70), Color(0xFFC98500))
        else listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF8A55D0), Color(0xFF1BAF7A), Color(0xFFEDA100)),
        track = if (dark) Color(0xFF383835) else Color(0xFFE1E0D9),
        gap = scheme.surfaceContainerLow,
    )
    CompositionLocalProvider(LocalData provides data) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
