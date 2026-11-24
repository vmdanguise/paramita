package ar.param.advisor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

// Sans (Roboto del sistema, igual que paramita.org) para la interfaz.
// Serif para títulos y textos de práctica, en sintonía con el logotipo de Paramita (REQ-041).
val ManiTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 32.sp,
    ),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
)
