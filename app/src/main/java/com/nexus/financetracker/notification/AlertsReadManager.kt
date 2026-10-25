package com.nexus.financetracker.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks read/unread status for notifications and in-app alerts.
 * Persists read alert IDs in SharedPreferences.
 */
@Singleton
class AlertsReadManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("nexus_alerts_read", Context.MODE_PRIVATE)
    private val KEY_READ_IDS = "read_alert_ids"

    private val _readIds = MutableStateFlow<Set<String>>(loadReadIds())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    private fun loadReadIds(): Set<String> {
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    fun isRead(alertId: String): Boolean {
        return _readIds.value.contains(alertId)
    }

    fun markAsRead(alertId: String) {
        val updated = _readIds.value + alertId
        prefs.edit().putStringSet(KEY_READ_IDS, updated).apply()
        _readIds.value = updated
    }

    fun markAllAsRead(alertIds: Collection<String>) {
        val updated = _readIds.value + alertIds
        prefs.edit().putStringSet(KEY_READ_IDS, updated).apply()
        _readIds.value = updated
    }
}
