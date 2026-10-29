package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.data.local.dao.CategoryDao
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.data.local.entity.CategoryEntity
import com.nexus.financetracker.domain.model.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CategoryRepository connecting to Firestore "categories" collection
 * with Room local read cache and queued writes for full offline support.
 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : CategoryRepository {

    private val categoriesCollection = firestore.collection("categories")
    private val expensesCollection = firestore.collection("expenses")
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getCategories(userId: String): Flow<List<Category>> = callbackFlow {
        // 1. Room acts as primary reactive read cache
        val roomJob = repositoryScope.launch {
            categoryDao.getCategoriesByUser(userId).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        // 2. Real-time Firestore snapshot listener syncs remote changes into Room
        val listener = categoriesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    repositoryScope.launch {
                        for (change in snapshot.documentChanges) {
                            when (change.type) {
                                DocumentChange.Type.ADDED,
                                DocumentChange.Type.MODIFIED -> {
                                    val cat = change.document.toObject(Category::class.java)
                                        ?.copy(id = change.document.id)
                                    if (cat != null) {
                                        try {
                                            categoryDao.insertCategory(CategoryEntity.fromDomain(cat))
                                        } catch (_: Exception) {}
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    try {
                                        categoryDao.deleteCategoryById(change.document.id)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }

        awaitClose {
            roomJob.cancel()
            listener.remove()
        }
    }

    override suspend fun addCategory(category: Category): String {
        val validation = category.validate()
        if (validation != null) {
            throw IllegalArgumentException(validation)
        }

        // Client-side ID generation avoids blocking on network availability
        val docRef = if (category.id.isNotBlank()) categoriesCollection.document(category.id) else categoriesCollection.document()
        val withId = category.copy(id = docRef.id)

        // 1. Write to Room local cache first
        try {
            categoryDao.insertCategory(CategoryEntity.fromDomain(withId))
        } catch (_: Exception) {}

        // 2. Queue write in Firestore persistent cache
        try {
            docRef.set(withId.toFirestoreMap()).await()
        } catch (_: Exception) {
            try {
                docRef.set(withId.toLegacyFirestoreMap()).await()
            } catch (_: Exception) {}
        }

        return docRef.id
    }

    override suspend fun renameCategory(categoryId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Category name cannot be empty")
        if (trimmed.length > 40) throw IllegalArgumentException("Category name cannot exceed 40 characters")

        // 1. Update Room first
        try {
            categoryDao.renameCategory(categoryId, trimmed)
        } catch (_: Exception) {}

        // 2. Queue in Firestore
        try {
            categoriesCollection.document(categoryId).update("name", trimmed).await()
        } catch (_: Exception) {}
    }

    override suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String) {
        if (sourceCategoryId == targetCategoryId) return

        // 1. Remap in Room offline cache immediately
        try {
            expenseDao.remapExpenseCategories(sourceCategoryId, targetCategoryId)
            categoryDao.markCategoryMerged(sourceCategoryId, targetCategoryId)
        } catch (_: Exception) {}

        // 2. Query and remap in Firestore
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
        // 1. Delete from Room first
        try {
            categoryDao.deleteCategoryById(categoryId)
        } catch (_: Exception) {}

        // 2. Queue in Firestore
        try {
            categoriesCollection.document(categoryId).delete().await()
        } catch (_: Exception) {}
    }

    override suspend fun clearLocalCache(userId: String) {
        try {
            categoryDao.deleteAllForUser(userId)
        } catch (_: Exception) {}
    }
}
