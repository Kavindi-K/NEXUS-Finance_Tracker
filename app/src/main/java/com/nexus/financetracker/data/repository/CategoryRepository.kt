package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for categories (categories/{categoryId}).
 * Conforms to Member 2 Order 5 (feature/expense-categories).
 */
interface CategoryRepository {
    fun getCategories(userId: String): Flow<List<Category>>
    suspend fun addCategory(category: Category): String
    suspend fun updateCategory(category: Category)
    suspend fun renameCategory(categoryId: String, newName: String)
    suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String)
    suspend fun deleteCategory(categoryId: String)
    suspend fun clearLocalCache(userId: String)
}
