package com.nexus.financetracker.data.remote

import android.util.Log
import com.nexus.financetracker.domain.model.ExchangeRateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source for fetching live and historical currency conversion rates (FR4).
 * - For latest rates: uses open.er-api.com (free, high quota) with Open Exchange Rates as fallback.
 * - For past dates: queries Open Exchange Rates historical endpoint using registered App ID.
 */
@Singleton
class ExchangeRateRemoteDataSource @Inject constructor() {

    companion object {
        private const val TAG = "ExchangeRateRemote"
        private const val OPEN_EXCHANGE_RATES_APP_ID = "1a799d4187524f50a257902ea61f1af6"
        private const val FREE_LATEST_BASE_URL = "https://open.er-api.com/v6/latest"
        private const val OXR_BASE_URL = "https://openexchangerates.org/api"
        const val DEFAULT_USD_LKR_RATE = 320.0
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

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

    /**
     * Fetches exchange rate for a given base currency to target currency (e.g. USD -> LKR).
     * If [date] is provided and represents a past date, looks up historical rate for that exact day.
     * Otherwise fetches the latest market rate.
     */
    suspend fun getExchangeRate(
        baseCurrency: String = "USD",
        targetCurrency: String = "LKR",
        date: Date? = null
    ): Result<ExchangeRateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseCurrency.uppercase()
            val target = targetCurrency.uppercase()

            if (date != null && isPastDate(date)) {
                val dateString = dateFormat.format(date)
                fetchHistoricalRate(base, target, dateString, date)
            } else {
                fetchLatestRate(base, target)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to fetch exchange rate for $baseCurrency->$targetCurrency: ${e.message}")
        }
    }

    private fun fetchHistoricalRate(
        base: String,
        target: String,
        dateString: String,
        originalDate: Date
    ): ExchangeRateResult {
        val url = "$OXR_BASE_URL/historical/$dateString.json?app_id=$OPEN_EXCHANGE_RATES_APP_ID&symbols=$target"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NexusFinanceTracker/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Historical API error: HTTP ${response.code}")
        }

        val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
        val json = JSONObject(body)

        if (json.optBoolean("error", false)) {
            throw IllegalStateException("API error: ${json.optString("description", "Unknown error")}")
        }

        val rates = json.getJSONObject("rates")
        if (!rates.has(target)) {
            throw IllegalArgumentException("Target currency $target not found in historical rates")
        }

        val rate = rates.getDouble(target)
        val timeUnix = json.optLong("timestamp", originalDate.time / 1000)

        return ExchangeRateResult(
            baseCurrency = base,
            targetCurrency = target,
            rate = rate,
            timestamp = Date(timeUnix * 1000),
            isLive = true
        )
    }

    private fun fetchLatestRate(base: String, target: String): ExchangeRateResult {
        return try {
            val url = "$FREE_LATEST_BASE_URL/$base"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NexusFinanceTracker/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val json = JSONObject(body)

            if (json.optString("result") != "success") {
                throw IllegalStateException("API error: ${json.optString("error-type")}")
            }

            val rates = json.getJSONObject("rates")
            val rate = rates.getDouble(target)
            val timeUnix = json.optLong("time_last_update_unix", System.currentTimeMillis() / 1000)

            ExchangeRateResult(
                baseCurrency = base,
                targetCurrency = target,
                rate = rate,
                timestamp = Date(timeUnix * 1000),
                isLive = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "open.er-api failed, falling back to Open Exchange Rates latest: ${e.message}")
            val url = "$OXR_BASE_URL/latest.json?app_id=$OPEN_EXCHANGE_RATES_APP_ID&symbols=$target"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NexusFinanceTracker/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IllegalStateException("Fallback API error: HTTP ${response.code}")

            val body = response.body?.string() ?: throw IllegalStateException("Empty body")
            val json = JSONObject(body)
            val rates = json.getJSONObject("rates")
            val rate = rates.getDouble(target)
            val timeUnix = json.optLong("timestamp", System.currentTimeMillis() / 1000)

            ExchangeRateResult(
                baseCurrency = base,
                targetCurrency = target,
                rate = rate,
                timestamp = Date(timeUnix * 1000),
                isLive = true
            )
        }
    }
}
