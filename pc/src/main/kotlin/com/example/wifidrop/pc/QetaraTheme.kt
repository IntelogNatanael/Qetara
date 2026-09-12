package com.example.wifidrop.pc

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bundled OFL fonts keep the desktop layout consistent without network access.
private val qetaraFontFamily = FontFamily(
    Font("fonts/Inter-Regular.ttf", weight = FontWeight.Normal),
    Font("fonts/Inter-Medium.ttf", weight = FontWeight.Medium),
    Font("fonts/Inter-SemiBold.ttf", weight = FontWeight.SemiBold),
    Font("fonts/Inter-Bold.ttf", weight = FontWeight.Bold)
)

internal val qetaraTypography = Typography(
    defaultFontFamily = qetaraFontFamily,
    h4 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    h5 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    subtitle1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    subtitle2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    body1 = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    body2 = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    caption = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    overline = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
)

internal val qetaraShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)
