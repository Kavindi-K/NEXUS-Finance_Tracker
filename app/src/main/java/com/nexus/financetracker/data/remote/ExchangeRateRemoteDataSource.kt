package com.nexus.financetracker.data.remote

import android.util.Log
import com.nexus.financetracker.domain.model.ExchangeRateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source for fetching live currency conversion rates (FR4).
 * Connects to open.er-api.com (100% free, no API key required).
 */
@Singleton
class ExchangeRateRemoteDataSource @Inject constructor() {

    companion object {
        private const val TAG = "ExchangeRateRemote"
        private const val BASE_URL = "https://open.er-api.com/v6/latest"
        const val DEFAULT_USD_LKR_RATE = 320.0
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetches live exchange rate for a given base currency to target currency (e.g. USD -> LKR).
     * Returns Result with ExchangeRateResult, or Failure if offline/network error.
     */
    suspend fun getExchangeRate(
        baseCurrency: String = "USD",
        targetCurrency: String = "LKR"
    ): Result<ExchangeRateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE_URL/${baseCurrency.uppercase()}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NexusFinanceTracker/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("API error: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
            val json = JSONObject(body)

            if (json.optString("result") != "success") {
                throw IllegalStateException("API reported failure: ${json.optString("error-type")}")
            }

            val rates = json.getJSONObject("rates")
            if (!rates.has(targetCurrency.uppercase())) {
                throw IllegalArgumentException("Target currency $targetCurrency not found in rates")
            }

            val rate = rates.getDouble(targetCurrency.uppercase())
            val timeUnix = json.optLong("time_last_update_unix", System.currentTimeMillis() / 1000)

            ExchangeRateResult(
                baseCurrency = baseCurrency.uppercase(),
                targetCurrency = targetCurrency.uppercase(),
                rate = rate,
                timestamp = Date(timeUnix * 1000),
                isLive = true
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed to fetch live rate for $baseCurrency->$targetCurrency: ${e.message}")
        }
    }
}
