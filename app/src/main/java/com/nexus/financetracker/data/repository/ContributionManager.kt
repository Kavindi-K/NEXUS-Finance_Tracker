package com.nexus.financetracker.data.repository



import android.content.Context

import com.nexus.financetracker.domain.model.Contribution

import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import org.json.JSONArray

import org.json.JSONObject

import java.util.Date

import java.util.UUID

import javax.inject.Inject

import javax.inject.Singleton



@Singleton

class ContributionManager @Inject constructor(

    @param:ApplicationContext private val context: Context

) {

    private val prefs = context.getSharedPreferences("nexus_contributions_prefs", Context.MODE_PRIVATE)

    private val KEY_CONTRIBUTIONS = "saved_contributions_json"

    private val KEY_REVIEWED_CATEGORIES = "reviewed_categories_set"

    private val KEY_SAVINGS_ROLLOVER_PREFIX = "savings_rollover_"

    private val KEY_SAVINGS_REVIEWED_PREFIX = "savings_reviewed_"



    private val _contributions = MutableStateFlow<List<Contribution>>(loadContributions())

    val contributions: StateFlow<List<Contribution>> = _contributions.asStateFlow()



    private val _reviewedCategories = MutableStateFlow<Set<String>>(loadReviewedCategories())

    val reviewedCategories: StateFlow<Set<String>> = _reviewedCategories.asStateFlow()



    private val _savingsUpdateTrigger = MutableStateFlow(System.currentTimeMillis())

    val savingsUpdateTrigger: StateFlow<Long> = _savingsUpdateTrigger.asStateFlow()



    private fun loadContributions(): List<Contribution> {

        val jsonString = prefs.getString(KEY_CONTRIBUTIONS, null) ?: return emptyList()

        return try {

            val jsonArray = JSONArray(jsonString)

            val list = mutableListOf<Contribution>()

            for (i in 0 until jsonArray.length()) {

                val obj = jsonArray.getJSONObject(i)

                list.add(

                    Contribution(

                        id = obj.optString("id", UUID.randomUUID().toString()),

                        userId = obj.optString("userId", ""),

                        categoryId = obj.optString("categoryId", ""),

                        categoryName = obj.optString("categoryName", "Category").let {
                            if (it.startsWith("Monthly Savings Surplus", ignoreCase = true)) "Monthly Savings Surplus" else it
                        },

                        amountLKR = obj.optDouble("amountLKR", 0.0),

                        monthLabel = obj.optString("monthLabel", ""),

                        isAllocated = obj.optBoolean("isAllocated", false),

                        allocatedGoalId = obj.optString("allocatedGoalId").takeIf { it.isNotBlank() },

                        allocatedGoalTitle = obj.optString("allocatedGoalTitle").takeIf { it.isNotBlank() },

                        createdAt = Date(obj.optLong("createdAt", System.currentTimeMillis()))

                    )

                )

            }

            list.sortedByDescending { it.createdAt }

        } catch (_: Exception) {

            emptyList()

        }

    }



    private fun loadReviewedCategories(): Set<String> {

        return prefs.getStringSet(KEY_REVIEWED_CATEGORIES, emptySet())?.toSet() ?: emptySet()

    }



    private fun saveContributions(list: List<Contribution>) {

        val jsonArray = JSONArray()

        for (item in list) {

            val obj = JSONObject().apply {

                put("id", item.id)

                put("userId", item.userId)

                put("categoryId", item.categoryId)

                put("categoryName", item.categoryName)

                put("amountLKR", item.amountLKR)

                put("monthLabel", item.monthLabel)

                put("isAllocated", item.isAllocated)

                put("allocatedGoalId", item.allocatedGoalId ?: "")

                put("allocatedGoalTitle", item.allocatedGoalTitle ?: "")

                put("createdAt", item.createdAt.time)

            }

            jsonArray.put(obj)

        }

        prefs.edit().putString(KEY_CONTRIBUTIONS, jsonArray.toString()).apply()

        _contributions.value = list.sortedByDescending { it.createdAt }

    }



    fun isCategoryReviewed(categoryId: String, monthLabel: String): Boolean {

        val key = "${categoryId}_$monthLabel"

        return _reviewedCategories.value.contains(key)

    }



    fun markCategoryReviewed(categoryId: String, monthLabel: String) {

        val key = "${categoryId}_$monthLabel"

        val updated = _reviewedCategories.value.toMutableSet()

        updated.add(key)

        prefs.edit().putStringSet(KEY_REVIEWED_CATEGORIES, updated).apply()

        _reviewedCategories.value = updated

    }



    fun addContribution(

        userId: String,

        categoryId: String,

        categoryName: String,

        amountLKR: Double,

        monthLabel: String

    ): Contribution {

        val newContribution = Contribution(

            id = UUID.randomUUID().toString(),

            userId = userId,

            categoryId = categoryId,

            categoryName = categoryName,

            amountLKR = amountLKR,

            monthLabel = monthLabel,

            isAllocated = false,

            createdAt = Date()

        )

        val current = _contributions.value.toMutableList()

        current.add(0, newContribution)

        saveContributions(current)

        markCategoryReviewed(categoryId, monthLabel)

        return newContribution

    }



    fun allocateContribution(contributionId: String, goalId: String, goalTitle: String) {

        val updated = _contributions.value.map { item ->

            if (item.id == contributionId) {

                item.copy(

                    isAllocated = true,

                    allocatedGoalId = goalId,

                    allocatedGoalTitle = goalTitle

                )

            } else {

                item

            }

        }

        saveContributions(updated)

    }



    fun deleteContribution(contributionId: String) {

        val updated = _contributions.value.filter { it.id != contributionId }

        saveContributions(updated)

    }



    fun getRolloverSavings(userId: String, monthLabel: String): Double {

        val key = "${KEY_SAVINGS_ROLLOVER_PREFIX}${userId}_${monthLabel}"

        return prefs.getString(key, null)?.toDoubleOrNull() ?: 0.0

    }



    fun isMonthlySavingsReviewed(userId: String, monthLabel: String): Boolean {

        val key = "${KEY_SAVINGS_REVIEWED_PREFIX}${userId}_${monthLabel}"

        return prefs.getBoolean(key, false)

    }



    fun recordMonthlySavingsAllocation(

        userId: String,

        monthLabel: String,

        totalSavingsLKR: Double,

        allocatedPercent: Double,

        contributionAmountLKR: Double,

        rolloverAmountLKR: Double,

        nextMonthLabel: String

    ): Contribution? {

        var newContribution: Contribution? = null

        if (contributionAmountLKR > 0.0) {

            newContribution = addContribution(

                userId = userId,

                categoryId = "monthly_savings",

                categoryName = "Monthly Savings Surplus",

                amountLKR = contributionAmountLKR,

                monthLabel = monthLabel

            )

        }



        val rolloverKey = "${KEY_SAVINGS_ROLLOVER_PREFIX}${userId}_${nextMonthLabel}"

        val currentRollover = prefs.getString(rolloverKey, null)?.toDoubleOrNull() ?: 0.0

        val updatedRollover = currentRollover + rolloverAmountLKR

        prefs.edit()

            .putString(rolloverKey, updatedRollover.toString())

            .putBoolean("${KEY_SAVINGS_REVIEWED_PREFIX}${userId}_${monthLabel}", true)

            .apply()



        _savingsUpdateTrigger.value = System.currentTimeMillis()

        return newContribution

    }



    fun resetSavingsReview(userId: String, monthLabel: String, nextMonthLabel: String? = null) {

        val editor = prefs.edit().remove("${KEY_SAVINGS_REVIEWED_PREFIX}${userId}_${monthLabel}")

        if (nextMonthLabel != null) {

            editor.remove("${KEY_SAVINGS_ROLLOVER_PREFIX}${userId}_${nextMonthLabel}")

        }

        editor.apply()

        _savingsUpdateTrigger.value = System.currentTimeMillis()

    }

}

