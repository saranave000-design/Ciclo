package it.saranave.ciclo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF9C2F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3E0020),
    secondary = Color(0xFF6E4F7A),
    secondaryContainer = Color(0xFFF1DCF7),
    onSecondaryContainer = Color(0xFF281533),
    background = Color(0xFFFFF8F9),
    surface = Color(0xFFFFF8F9),
    surfaceVariant = Color(0xFFF4E3E8),
    surfaceContainer = Color(0xFFF8ECEF),
    surfaceContainerHigh = Color(0xFFF4E3E8),
    surfaceContainerLow = Color(0xFFFCF1F3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB0C9),
    onPrimary = Color(0xFF5E1134),
    primaryContainer = Color(0xFF7D2A4B),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFD9BDE3),
    secondaryContainer = Color(0xFF54395F),
    onSecondaryContainer = Color(0xFFF1DCF7),
    background = Color(0xFF1C1619),
    surface = Color(0xFF1C1619),
    surfaceVariant = Color(0xFF3A2D33),
    surfaceContainer = Color(0xFF2A2125),
    surfaceContainerHigh = Color(0xFF34292E),
    surfaceContainerLow = Color(0xFF241C20),
)

@Composable
fun CicloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

val PeriodColors = listOf(Color(0xFFF48FB1), Color(0xFFEC407A), Color(0xFFC2185B), Color(0xFF880E4F))
val LevelColors = listOf(Color(0xFF5E9E62), Color(0xFFE0A526), Color(0xFFE86A3A), Color(0xFFC62828))
val RosaceaColor = Color(0xFFE0457B)
val DsColor = Color(0xFFD9A23A)
