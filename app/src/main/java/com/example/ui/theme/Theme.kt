package com.example.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val RouterScanColorScheme = darkColorScheme(
    primary = NeonGreen, secondary = CyberTeal, tertiary = CyberAmber,
    background = CyberSlateBg, surface = CyberCardBg,
    onPrimary = CyberSlateBg, onSecondary = Color.White,
    onBackground = CustomWhite, onSurface = CustomWhite
)
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RouterScanColorScheme, typography = Typography, content = content)
}
