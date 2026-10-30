package com.nexus.financetracker.data.repository

import com.nexus.financetracker.data.remote.ExchangeRateRemoteDataSource
import com.nexus.financetracker.domain.model.ExchangeRateResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    private val remoteDataSource: ExchangeRateRemoteDataSource
) : ExchangeRateRepository {

    // In-memory cache: "USD_LKR_2026-10-25" or "USD_LKR_latest" -> ExchangeRateResult
    private val cache = ConcurrentHashMap<String, ExchangeRateResult>()
    
    // Cache validity for latest: 1 hour in milliseconds
    private val cacheDurationMs = 60 * 60 * 1000L
    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    private fun isPastDate(date: Date): Boolean {
        val entryCal = Calendar.getInstance().apply { time = date }
        val todayCal = Calendar.getInstance()

        entryCal.set(Calendar.HOUR_OF_DAY, 0)
        entryCal.set(Calendar.MINUTE, 0)
        entryCal.set(Calendar.SECOND, 0)
        entryCal.set(Calendar.MILLISECOND, 0)

        todayCal.set(Calendar.HOUR_OF_DAY, 0)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        todayCal.set(Calendar.MILLISECOND, 0)

        return entryCal.before(todayCal)
    }

    override suspend fun getExchangeRate(
        baseCurrency: String,
        targetCurrency: String,
        date: Date?
    ): ExchangeRateResult {
        val base = baseCurrency.uppercase()
        val target = targetCurrency.uppercase()

        // Same currency conversion is always 1.0
        if (base == target) {
            return ExchangeRateResult(
                baseCurrency = base,
                targetCurrency = target,
                rate = 1.0,
                timestamp = date ?: Date(),
                isLive = true
            )
        }

        val dateKey = if (date != null && isPastDate(date)) dateFormat.format(date) else "latest"
        val cacheKey = "${base}_${target}_$dateKey"
        val cached = cache[cacheKey]
        val now = System.currentTimeMillis()

        // For past dates: historical rates are final, so cached values are indefinitely valid.
        // For latest: check 1 hour TTL.
        val isCacheValid = if (dateKey != "latest") {
            cached != null
        } else {
            cached != null && (now - cached.timestamp.time) < cacheDurationMs
        }

        if (isCacheValid && cached != null) {
            return cached
        }

        // Try fetching fresh rate from network
        val result = remoteDataSource.getExchangeRate(base, target, date)
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
                    timestamp = date ?: Date(),
                    isLive = false
                )
            }
        )
    }
}
