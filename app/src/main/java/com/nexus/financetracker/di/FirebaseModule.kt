package com.nexus.financetracker.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Firebase Auth and Firestore singletons
 * with persistent local disk caching configured for seamless offline operation (Member 2 Order 7).
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        try {
            // Configure bounded 100 MB persistent cache with automatic LRU garbage collection
            // to prevent unnecessary computer/device storage bloat while guaranteeing offline capability
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(104857600L) // 100 MB bound with automatic LRU cleanup
                        .build()
                )
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) {
            // Settings can only be configured once before any Firestore operation
        }
        return firestore
    }
}
