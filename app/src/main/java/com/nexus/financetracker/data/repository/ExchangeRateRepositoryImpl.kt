package com.nexus.financetracker.data.repository

import com.nexus.financetracker.data.remote.ExchangeRateRemoteDataSource
import com.nexus.financetracker.domain.model.ExchangeRateResult
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    private val remoteDataSource: ExchangeRateRemoteDataSource
) : ExchangeRateRepository {

    // In-memory cache: "USD_LKR" -> ExchangeRateResult
    private val cache = ConcurrentHashMap<String, ExchangeRateResult>()
    
    // Cache validity: 1 hour in milliseconds
    private val cacheDurationMs = 60 * 60 * 1000L

    override suspend fun getExchangeRate(
        baseCurrency: String,
        targetCurrency: String
    ): ExchangeRateResult {
        val base = baseCurrency.uppercase()
        val target = targetCurrency.uppercase()

        // Same currency conversion is always 1.0
        if (base == target) {
            return ExchangeRateResult(
                baseCurrency = base,
                targetCurrency = target,
                rate = 1.0,
                timestamp = Date(),
                isLive = true
            )
        }

        val cacheKey = "${base}_${target}"
        val cached = cache[cacheKey]
        val now = System.currentTimeMillis()

        // If cache exists and is less than 1 hour old, return it
        if (cached != null && (now - cached.timestamp.time) < cacheDurationMs) {
            return cached
        }

        // Try fetching fresh rate from network
        val result = remoteDataSource.getExchangeRate(base, target)
        return result.fold(
            onSuccess = { rateResult ->
                cache[cacheKey] = rateResult
                rateResult
            },
            onFailure = {
                // If offline / network error:
                // 1. Return stale cache if available
                // 2. Otherwise return safe default fallback (320.0 for USD -> LKR)
                cached ?: ExchangeRateResult(
                    baseCurrency = base,
                    targetCurrency = target,
                    rate = if (base == "USD" && target == "LKR") ExchangeRateRemoteDataSource.DEFAULT_USD_LKR_RATE else 1.0,
                    timestamp = Date(),
                    isLive = false
                )
            }
        )
    }
}
