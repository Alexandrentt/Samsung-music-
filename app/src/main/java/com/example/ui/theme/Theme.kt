package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SamsungBlueDark,
    onPrimary = Color.White,
    primaryContainer = SamsungBlueDark.copy(alpha = 0.2f),
    onPrimaryContainer = SamsungBlueLight,
    secondary = SamsungAccentPurple,
    onSecondary = Color.White,
    background = SamsungDarkBackground,
    surface = SamsungDarkSurface,
    surfaceVariant = SamsungDarkSurfaceVariant,
    onBackground = SamsungDarkTextPrimary,
    onSurface = SamsungDarkTextPrimary,
    onSurfaceVariant = SamsungDarkTextSecondary,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SamsungBlue,
    onPrimary = Color.White,
    primaryContainer = SamsungBlueLight,
    onPrimaryContainer = SamsungBlue,
    secondary = SamsungAccentPurple,
    onSecondary = Color.White,
    background = SamsungLightBackground,
    surface = SamsungLightSurface,
    surfaceVariant = SamsungLightSurfaceVariant,
    onBackground = SamsungLightTextPrimary,
    onSurface = SamsungLightTextPrimary,
    onSurfaceVariant = SamsungLightTextSecondary,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
