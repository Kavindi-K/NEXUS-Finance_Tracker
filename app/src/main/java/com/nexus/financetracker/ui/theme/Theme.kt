package com.nexus.financetracker.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nexus.financetracker.data.local.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = NexusNavyPrimaryDark,
    onPrimary = NexusNavyOnPrimaryDark,
    primaryContainer = NexusNavyContainerDark,
    onPrimaryContainer = NexusNavyOnContainerDark,
    secondary = NexusSlateSecondaryDark,
    onSecondary = NexusSlateOnSecondaryDark,
    secondaryContainer = NexusSlateContainerDark,
    onSecondaryContainer = NexusSlateOnContainerDark,
    tertiary = NexusOceanTertiaryDark,
    onTertiary = NexusOceanOnTertiaryDark,
    tertiaryContainer = NexusOceanTertiaryContainerDark,
    onTertiaryContainer = NexusOceanOnTertiaryContainerDark,
    background = NexusBackgroundDark,
    onBackground = NexusOnBackgroundDark,
    surface = NexusSurfaceDark,
    onSurface = NexusOnSurfaceDark,
    surfaceVariant = NexusSurfaceVariantDark,
    onSurfaceVariant = NexusOnSurfaceVariantDark,
    outline = NexusOutlineDark,
    error = StatusLoss
)

private val LightColorScheme = lightColorScheme(
    primary = NexusNavyPrimary,
    onPrimary = NexusNavyOnPrimary,
    primaryContainer = NexusNavyContainer,
    onPrimaryContainer = NexusNavyOnContainer,
    secondary = NexusSlateSecondary,
    onSecondary = NexusSlateOnSecondary,
    secondaryContainer = NexusSlateContainer,
    onSecondaryContainer = NexusSlateOnContainer,
    tertiary = NexusOceanTertiary,
    onTertiary = NexusOceanOnTertiary,
    tertiaryContainer = NexusOceanTertiaryContainer,
    onTertiaryContainer = NexusOceanOnTertiaryContainer,
    background = NexusBackgroundLight,
    onBackground = NexusOnBackgroundLight,
    surface = NexusSurfaceLight,
    onSurface = NexusOnSurfaceLight,
    surfaceVariant = NexusSurfaceVariantLight,
    onSurfaceVariant = NexusOnSurfaceVariantLight,
    outline = NexusOutlineLight,
    error = StatusLoss
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NEXUSFinanceTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    // Default false to preserve the official NEXUS Deep Navy brand identity from designs
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // In dark theme: status & nav bar icons must be light (white)
                // In light theme: status & nav bar icons must be dark
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}