package com.nexus.financetracker.data.local.dao

import androidx.room.*
import com.nexus.financetracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for user custom category offline caching.
 */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE userId = :userId AND (mergingInto IS NULL OR mergingInto = '' OR mergingInto LIKE 'dur:%') ORDER BY name ASC")
    fun getCategoriesByUser(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId")
    suspend fun getCategoryCountByUser(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET name = :newName WHERE id = :id")
    suspend fun renameCategory(id: String, newName: String)

    @Query("UPDATE categories SET mergingInto = :targetId WHERE id = :sourceId")
    suspend fun markCategoryMerged(sourceId: String, targetId: String)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

