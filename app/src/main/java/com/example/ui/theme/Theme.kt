package com.example.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val scheme = darkColorScheme(
    primary = Color(0xFF00FF88), secondary = Color(0xFF00D4FF),
    background = Color(0xFF0A0F1A), surface = Color(0xFF111827),
    onPrimary = Color(0xFF0A0F1A), onBackground = Color.White, onSurface = Color.White
)
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
