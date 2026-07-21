package com.schoollog.attendance.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SchoollogBlue,
    secondary = SchoollogGreen,
    background = SchoollogBackground,
    surface = SchoollogBackground,
    onSurface = SchoollogOnSurface,
)

@Composable
fun SchoollogAttendanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
