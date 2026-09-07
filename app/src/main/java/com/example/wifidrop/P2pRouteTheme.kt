package com.example.wifidrop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val QetaraCream = Color(0xFFFFF7ED)
private val QetaraInk = Color(0xFF102A43)
private val QetaraSurface = Color(0xFFFFFCF8)
private val QetaraInkContainer = Color(0xFFE7EEF3)
private val QetaraWarmContainer = Color(0xFFF4E7D9)
private val QetaraWarmAccent = Color(0xFFFFE7C8)
private val QetaraMutedInk = Color(0xFF5D7082)
private val QetaraOutline = Color(0xFFD9CBBE)
private val QetaraDarkSurface = Color(0xFF17324A)
private val QetaraDarkContainer = Color(0xFF223D56)

@Composable
fun QetaraAppShell() {
    QetaraTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            P2pScreenRoute()
        }
    }
}

@Composable
internal fun QetaraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    ProvidePrivateClipboard {
        MaterialTheme(
            colorScheme = if (dark) wifiDropDarkScheme else wifiDropLightScheme
        ) {
            content()
        }
    }
}

private val wifiDropLightScheme = lightColorScheme(
    primary = QetaraInk,
    onPrimary = QetaraCream,
    primaryContainer = QetaraInkContainer,
    onPrimaryContainer = QetaraInk,
    secondary = QetaraInk,
    onSecondary = QetaraCream,
    secondaryContainer = QetaraWarmContainer,
    onSecondaryContainer = QetaraInk,
    tertiary = QetaraInk,
    onTertiary = QetaraCream,
    tertiaryContainer = QetaraWarmAccent,
    onTertiaryContainer = QetaraInk,
    background = QetaraCream,
    onBackground = QetaraInk,
    error = Color(0xFFAC2828),
    onError = Color.White,
    surface = QetaraSurface,
    onSurface = QetaraInk,
    surfaceVariant = QetaraInkContainer,
    onSurfaceVariant = QetaraMutedInk,
    outline = QetaraOutline,
    errorContainer = Color(0xFFFCE0E0),
    onErrorContainer = Color(0xFF7A1D1D)
)

private val wifiDropDarkScheme = darkColorScheme(
    primary = QetaraCream,
    onPrimary = QetaraInk,
    primaryContainer = QetaraDarkContainer,
    onPrimaryContainer = QetaraCream,
    secondary = QetaraCream,
    onSecondary = QetaraInk,
    secondaryContainer = Color(0xFF42382E),
    onSecondaryContainer = QetaraCream,
    tertiary = QetaraCream,
    onTertiary = QetaraInk,
    tertiaryContainer = Color(0xFF4A3C2C),
    onTertiaryContainer = QetaraCream,
    background = QetaraInk,
    onBackground = QetaraCream,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surface = QetaraDarkSurface,
    onSurface = QetaraCream,
    surfaceVariant = QetaraDarkContainer,
    onSurfaceVariant = Color(0xFFD4C9BD),
    outline = Color(0xFF6E8294),
    errorContainer = Color(0xFF5A252A),
    onErrorContainer = Color(0xFFFFD9DB)
)
