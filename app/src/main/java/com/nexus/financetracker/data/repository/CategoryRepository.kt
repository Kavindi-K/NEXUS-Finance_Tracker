package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for categories (categories/{categoryId}).
 * Coordinated with Member 2.
 */
interface CategoryRepository {
    fun getCategories(userId: String): Flow<List<Category>>
    suspend fun addCategory(category: Category): String
    suspend fun renameCategory(categoryId: String, newName: String)
    suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String)
}
