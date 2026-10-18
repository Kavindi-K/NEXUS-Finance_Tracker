package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.domain.model.Category
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CategoryRepository connecting to Firestore "categories" collection.
 * Conforms to Technical Document Section 5.1/5.2.
 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : CategoryRepository {

    private val categoriesCollection = firestore.collection("categories")

    override fun getCategories(userId: String): Flow<List<Category>> = callbackFlow {
        val listener = categoriesCollection
            .whereEqualTo("userId", userId)
            .orderBy("name", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Category::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addCategory(category: Category): String {
        val docRef = categoriesCollection.add(category).await()
        return docRef.id
    }

    override suspend fun renameCategory(categoryId: String, newName: String) {
        categoriesCollection.document(categoryId).update("name", newName).await()
    }

    override suspend fun mergeCategories(sourceCategoryId: String, targetCategoryId: String) {
        categoriesCollection.document(sourceCategoryId).update("mergingInto", targetCategoryId).await()
    }
}
