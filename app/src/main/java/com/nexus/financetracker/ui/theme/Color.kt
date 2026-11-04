package com.nexus.financetracker.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// NEXUS Deep Sapphire Navy Design System (Matching Figma/Design Mockups)
// =========================================================================

// --- Light Scheme (Primary) ---
val NexusNavyPrimary = Color(0xFF0B3B6F)          // Deep Sapphire Navy from mockup header & buttons
val NexusNavyOnPrimary = Color(0xFFFFFFFF)
val NexusNavyContainer = Color(0xFF184C82)        // Balance card inside the navy header
val NexusNavyOnContainer = Color(0xFFFFFFFF)

val NexusSlateSecondary = Color(0xFF2E5B88)
val NexusSlateOnSecondary = Color(0xFFFFFFFF)
val NexusSlateContainer = Color(0xFFD6E4F4)
val NexusSlateOnContainer = Color(0xFF0B2542)

val NexusOceanTertiary = Color(0xFF1E6091)
val NexusOceanOnTertiary = Color(0xFFFFFFFF)
val NexusOceanTertiaryContainer = Color(0xFFD0E6FA)
val NexusOceanOnTertiaryContainer = Color(0xFF001E36)

val NexusBackgroundLight = Color(0xFFF3F6FA)       // Soft modern grey-blue canvas
val NexusOnBackgroundLight = Color(0xFF0F172A)
val NexusSurfaceLight = Color(0xFFFFFFFF)          // Crisp white container sheets
val NexusOnSurfaceLight = Color(0xFF0F172A)
val NexusSurfaceVariantLight = Color(0xFFE5ECF4)   // Metric pills and card backgrounds
val NexusOnSurfaceVariantLight = Color(0xFF475569) // Subtitle / secondary label text
val NexusOutlineLight = Color(0xFFCBD5E1)          // Subtle card borders

// --- Dark Scheme ---
val NexusNavyPrimaryDark = Color(0xFF7FAFE3)
val NexusNavyOnPrimaryDark = Color(0xFF042448)
val NexusNavyContainerDark = Color(0xFF0C3868)
val NexusNavyOnContainerDark = Color(0xFFD6E4F8)

val NexusSlateSecondaryDark = Color(0xFF9EBFE2)
val NexusSlateOnSecondaryDark = Color(0xFF123456)
val NexusSlateContainerDark = Color(0xFF224467)
val NexusSlateOnContainerDark = Color(0xFFD6E4F4)

val NexusOceanTertiaryDark = Color(0xFF90CCFA)
val NexusOceanOnTertiaryDark = Color(0xFF003358)
val NexusOceanTertiaryContainerDark = Color(0xFF0A4973)
val NexusOceanOnTertiaryContainerDark = Color(0xFFD0E6FA)

val NexusBackgroundDark = Color(0xFF0A121C)
val NexusOnBackgroundDark = Color(0xFFE2E8F0)
val NexusSurfaceDark = Color(0xFF101B28)
val NexusOnSurfaceDark = Color(0xFFE2E8F0)
val NexusSurfaceVariantDark = Color(0xFF1B2C3F)
val NexusOnSurfaceVariantDark = Color(0xFF94A3B8)
val NexusOutlineDark = Color(0xFF33485E)

// --- Semantic Financial Indicators (Exact Mockup Badges) ---
val StatusConfirmed = Color(0xFF22C55E)            // Green "Confirmed" badge
val StatusConfirmedContainer = Color(0xFFDCFCE7)
val StatusPending = Color(0xFFF59E0B)              // Orange "Pending" badge
val StatusPendingContainer = Color(0xFFFEF3C7)
val StatusLoss = Color(0xFFEF4444)                 // Red "Net P/L" badge
val StatusLossContainer = Color(0xFFFEE2E2)

// Alert Banner (Goal Screen "Slightly behind pace" banner)
val AlertBehindPace = Color(0xFF991B1B)
val AlertBehindPaceContainer = Color(0xFFFFD4D8)

// Backward-compatible semantic bindings
val IncomeGreen = StatusConfirmed
val IncomeGreenContainer = StatusConfirmedContainer
val ExpenseRed = StatusLoss
val ExpenseRedContainer = StatusLossContainer
val SavingsBlue = NexusNavyPrimary
val SavingsBlueContainer = NexusNavyContainer
val PendingAmber = StatusPending
val PendingAmberContainer = StatusPendingContainer
val TextMutedSlate = Color(0xFF64748B)