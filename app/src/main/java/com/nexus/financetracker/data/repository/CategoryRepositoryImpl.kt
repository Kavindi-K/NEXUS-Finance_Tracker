package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.domain.model.Category
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CategoryRepository connecting to Firestore "categories" collection
 * and managing Room local cache remapping.
 * Conforms to Member 2 Order 5 (feature/expense-categories).
 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val expenseDao: ExpenseDao
) : CategoryRepository {

    private val categoriesCollection = firestore.collection("categories")
    private val expensesCollection = firestore.collection("expenses")

    override fun getCategories(userId: String): Flow<List<Category>> = callbackFlow {
        val listener = categoriesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Category::class.java)?.copy(id = doc.id)
                }?.filter { it.mergingInto.isNullOrBlank() }
                    ?.sortedBy { it.name.lowercase() } ?: emptyList()

                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addCategory(category: Category): String {
        val validation = category.validate()
        if (validation != null) {
            throw IllegalArgumentException(validation)
        }
        val docRef = categoriesCollection.add(category.toFirestoreMap()).await()
        return docRef.id
    }

    override suspend fun renameCategory(categoryId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Category name cannot be empty")
        if (trimmed.length > 40) throw IllegalArgumentException("Category name cannot exceed 40 characters")
        categoriesCollection.document(categoryId).update("name", trimmed).await()
    }

    override suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String) {
        if (sourceCategoryId == targetCategoryId) return

        // 1. Remap all matching expenses in Room offline cache immediately
        try {
            expenseDao.remapExpenseCategories(sourceCategoryId, targetCategoryId)
        } catch (_: Exception) {}

        // 2. Query and remap all matching expenses in Firestore
        try {
            val matchingExpenses = expensesCollection
                .whereEqualTo("categoryId", sourceCategoryId)
                .get()
                .await()

            if (!matchingExpenses.isEmpty) {
                matchingExpenses.documents.chunked(400).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        batch.update(doc.reference, "categoryId", targetCategoryId)
                    }
                    batch.commit().await()
                }
            }
        } catch (_: Exception) {}

        // 3. Mark source category as merged in Firestore
        try {
            categoriesCollection.document(sourceCategoryId).update("mergingInto", targetCategoryId).await()
        } catch (_: Exception) {
            try {
                categoriesCollection.document(sourceCategoryId).delete().await()
            } catch (_: Exception) {}
        }
    }

    override suspend fun deleteCategory(categoryId: String) {
        categoriesCollection.document(categoryId).delete().await()
    }
}
