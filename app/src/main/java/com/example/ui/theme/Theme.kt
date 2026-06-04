package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD87B64),         // Softer sunset terracotta
    secondary = Color(0xFF2F2522),       // Card highlight container
    tertiary = Color(0xFF7CA972),        // Sage green
    background = Color(0xFF161311),      // Deep charcoal obsidian dark
    surface = Color(0xFF221C19),         // Rich warm dark card
    onPrimary = Color(0xFF161311),
    onSecondary = Color(0xFFE6D6CE),     // Cream text on secondary
    onBackground = Color(0xFFE6D6CE),    // Light cream text
    onSurface = Color(0xFFE6D6CE),       // Light cream text
    error = Color(0xFFE5533F)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ElectricTeal,
    secondary = SlateGrayLight,
    tertiary = BrightLime,
    background = CarbonBlack,
    surface = SlateCharcoal,
    onPrimary = CarbonBlack,
    onSecondary = ThemePrimaryText,
    onBackground = ThemePrimaryText,
    onSurface = ThemePrimaryText,
    error = DangerCoral
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
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
