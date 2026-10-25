package com.example.greenplanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoftGreenColors = lightColorScheme(
    primary = Color(0xFF397750),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ECDD),
    onPrimaryContainer = Color(0xFF193E28),
    secondary = Color(0xFF577B65),
    secondaryContainer = Color(0xFFDEF0E1),
    background = Color(0xFFF5F9F3),
    onBackground = Color(0xFF1E3025),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E3025),
    onSurfaceVariant = Color(0xFF65766A)
)

@Composable
fun GreenPlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SoftGreenColors, content = content)
}
