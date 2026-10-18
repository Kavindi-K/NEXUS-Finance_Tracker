package com.nexus.financetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun NEXUSFinanceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}