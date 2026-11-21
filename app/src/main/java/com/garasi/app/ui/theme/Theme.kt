package com.garasi.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm "garage sale" palette: orange + cream. Fixed brand colours (no dynamic colour).
private val Light = lightColorScheme(
    primary = Color(0xFFE8590C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D1), onPrimaryContainer = Color(0xFF5A1E00),
    secondary = Color(0xFF8A5A3B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE9DC), onSecondaryContainer = Color(0xFF3B200E),
    tertiary = Color(0xFF2E7D5B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F2E3), onTertiaryContainer = Color(0xFF0B3D27),
    background = Color(0xFFFFF8F2), onBackground = Color(0xFF231A15),
    surface = Color(0xFFFFF8F2), onSurface = Color(0xFF231A15),
    surfaceVariant = Color(0xFFF4E6DC), onSurfaceVariant = Color(0xFF6E5D52),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFCF9),
    surfaceContainer = Color(0xFFFBF1E9), surfaceContainerHigh = Color(0xFFF6EAE0),
    surfaceContainerHighest = Color(0xFFF1E3D8),
    outline = Color(0xFFD5C1B2), outlineVariant = Color(0xFFEADBD0),
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF9A5C), onPrimary = Color(0xFF4A1800),
    primaryContainer = Color(0xFF6B2A00), onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE6BEA6), onSecondary = Color(0xFF442A19),
    secondaryContainer = Color(0xFF4A3427), onSecondaryContainer = Color(0xFFFCE9DC),
    tertiary = Color(0xFF8ED6B2), onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF13513A), onTertiaryContainer = Color(0xFFD3F2E3),
    background = Color(0xFF17120F), onBackground = Color(0xFFF2E6DE),
    surface = Color(0xFF17120F), onSurface = Color(0xFFF2E6DE),
    surfaceVariant = Color(0xFF3A2F28), onSurfaceVariant = Color(0xFFD2C1B6),
    surfaceContainerLowest = Color(0xFF221B17), surfaceContainerLow = Color(0xFF241D18),
    surfaceContainer = Color(0xFF2A221D), surfaceContainerHigh = Color(0xFF332A24),
    surfaceContainerHighest = Color(0xFF3D332C),
    outline = Color(0xFF6A5A4F), outlineVariant = Color(0xFF4A3E36),
)

private val type = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = it.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = it.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = it.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // text fields
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GarasiTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = type, shapes = shapes, content = content)
}

val ScoreGreen = Color(0xFF2E9D5B)
val ScoreAmber = Color(0xFFF2A007)
val ScoreRed = Color(0xFFD64535)

val StatusDraft = Color(0xFF9A8B80)
val StatusReady = Color(0xFF2E9D5B)
val StatusPosted = Color(0xFF2F7DD1)
val StatusSold = Color(0xFFE8590C)
