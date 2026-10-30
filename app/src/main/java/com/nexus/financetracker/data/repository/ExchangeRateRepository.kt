package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.ExchangeRateResult
import java.util.Date

/**
 * Repository interface for obtaining foreign exchange rates (FR4).
 */
interface ExchangeRateRepository {
    /**
     * Gets exchange rate for baseCurrency to targetCurrency (defaults to USD -> LKR).
     * If [date] is provided, attempts historical rate lookup for past dates.
     * Returns a cached/live ExchangeRateResult, or a safe fallback if offline.
     */
    suspend fun getExchangeRate(
        baseCurrency: String = "USD",
        targetCurrency: String = "LKR",
        date: Date? = null
    ): ExchangeRateResult
}
