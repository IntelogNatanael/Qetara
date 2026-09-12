package com.example.wifidrop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val QetaraInter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun qetaraText(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = QetaraInter, fontSize = size.sp, lineHeight = line.sp,
        fontWeight = weight, letterSpacing = 0.sp)

@Composable
internal fun qetaraFilterChipColors() = FilterChipDefaults.filterChipColors(
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
)

private val QetaraTypography = Typography(
    displayLarge = qetaraText(48, 56, FontWeight.SemiBold),
    displayMedium = qetaraText(40, 48, FontWeight.SemiBold),
    displaySmall = qetaraText(36, 44, FontWeight.SemiBold),
    headlineLarge = qetaraText(30, 38, FontWeight.SemiBold),
    headlineMedium = qetaraText(26, 34, FontWeight.SemiBold),
    headlineSmall = qetaraText(24, 32, FontWeight.SemiBold),
    titleLarge = qetaraText(22, 28, FontWeight.SemiBold),
    titleMedium = qetaraText(16, 24, FontWeight.SemiBold),
    titleSmall = qetaraText(14, 20, FontWeight.SemiBold),
    bodyLarge = qetaraText(16, 24),
    bodyMedium = qetaraText(14, 22),
    bodySmall = qetaraText(12, 18),
    labelLarge = qetaraText(14, 20, FontWeight.SemiBold),
    labelMedium = qetaraText(12, 18, FontWeight.Medium),
    labelSmall = qetaraText(11, 16, FontWeight.Medium)
)

@Composable
fun QetaraAppShell() {
    QetaraTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            P2pScreenRoute()
        }
    }
}

@Composable
internal fun QetaraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    ProvidePrivateClipboard {
        MaterialTheme(
            colorScheme = if (dark) qetaraDarkScheme else qetaraLightScheme,
            typography = QetaraTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(24.dp)
            ),
            content = content
        )
    }
}

private val qetaraLightScheme = lightColorScheme(
    primary = Color(0xFF0A6B77), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF1F0), onPrimaryContainer = Color(0xFF075460),
    secondary = Color(0xFF536672), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0EF), onSecondaryContainer = Color(0xFF536672),
    tertiary = Color(0xFF765722), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7EBCD), onTertiaryContainer = Color(0xFF574016),
    background = Color(0xFFF4F6F5), onBackground = Color(0xFF102A43),
    surface = Color.White, onSurface = Color(0xFF102A43),
    surfaceVariant = Color(0xFFEAF0F0), onSurfaceVariant = Color(0xFF536672),
    surfaceDim = Color(0xFFDDE5E5), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFF0F4F3), surfaceContainerHigh = Color(0xFFEAF0EF),
    surfaceContainerHighest = Color(0xFFE2EAE9),
    outline = Color(0xFF788D94), outlineVariant = Color(0xFFDDE5E5),
    inverseSurface = Color(0xFF22373E), inverseOnSurface = Color(0xFFEAF0F0),
    inversePrimary = Color(0xFF88D3D7), surfaceTint = Color(0xFF0A6B77),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFFCE4E2), onErrorContainer = Color(0xFF7A1D1D)
)

private val qetaraDarkScheme = darkColorScheme(
    primary = Color(0xFF88D3D7), onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF104D55), onPrimaryContainer = Color(0xFFBCEFF0),
    secondary = Color(0xFFAFC2CA), onSecondary = Color(0xFF22373E),
    secondaryContainer = Color(0xFF22343D), onSecondaryContainer = Color(0xFFAFC2CA),
    tertiary = Color(0xFFE4C78F), onTertiary = Color(0xFF422E09),
    tertiaryContainer = Color(0xFF584320), onTertiaryContainer = Color(0xFFF7EBCD),
    background = Color(0xFF101B22), onBackground = Color(0xFFE7EFF2),
    surface = Color(0xFF182831), onSurface = Color(0xFFE7EFF2),
    surfaceVariant = Color(0xFF2A3E47), onSurfaceVariant = Color(0xFFAFC2CA),
    surfaceDim = Color(0xFF101B22), surfaceBright = Color(0xFF32434C),
    surfaceContainerLowest = Color(0xFF0C171D), surfaceContainerLow = Color(0xFF14232B),
    surfaceContainer = Color(0xFF182831), surfaceContainerHigh = Color(0xFF22343D),
    surfaceContainerHighest = Color(0xFF2A3E47),
    outline = Color(0xFF7D959F), outlineVariant = Color(0xFF344A54),
    inverseSurface = Color(0xFFE1E9EC), inverseOnSurface = Color(0xFF22373E),
    inversePrimary = Color(0xFF0A6B77), surfaceTint = Color(0xFF88D3D7),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A252A), onErrorContainer = Color(0xFFFFD9DB)
)
