package com.nexus.financetracker.domain.model

import java.util.Date

/**
 * Domain model representing exchange rate conversion data (FR4).
 */
data class ExchangeRateResult(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val timestamp: Date = Date(),
    val isLive: Boolean = true
)
