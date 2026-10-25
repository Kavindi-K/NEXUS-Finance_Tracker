package com.nexus.financetracker.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks read, seen, and deleted status for notifications and in-app alerts.
 * - [readIds]: Persists IDs of alerts specifically clicked/confirmed by the user.
 * - [seenIds]: Persists IDs of alerts that were active when the user last viewed the Alerts screen.
 *   This ensures the bottom navigation red dot disappears once Alerts is viewed, and reappears only
 *   when a genuinely new alert arrives.
 * - [deletedIds]: Persists IDs of alerts specifically deleted/dismissed by the user.
 */
@Singleton
class AlertsReadManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("nexus_alerts_read", Context.MODE_PRIVATE)
    private val KEY_READ_IDS = "read_alert_ids"
    private val KEY_SEEN_IDS = "seen_alert_ids"
    private val KEY_DELETED_IDS = "deleted_alert_ids"

    private val _readIds = MutableStateFlow<Set<String>>(loadIds(KEY_READ_IDS))
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    private val _seenIds = MutableStateFlow<Set<String>>(loadIds(KEY_SEEN_IDS))
    val seenIds: StateFlow<Set<String>> = _seenIds.asStateFlow()

    private val _deletedIds = MutableStateFlow<Set<String>>(loadIds(KEY_DELETED_IDS))
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    private fun loadIds(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
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
        if (alertIds.isEmpty()) return
        val updated = _readIds.value + alertIds
        prefs.edit().putStringSet(KEY_READ_IDS, updated).apply()
        _readIds.value = updated
    }

    /**
     * Marks all provided alert IDs as seen.
     * Clears the bottom navigation red dot until an alert with a new ID is generated.
     */
    fun markAllAsSeen(alertIds: Collection<String>) {
        if (alertIds.isEmpty()) return
        val updated = _seenIds.value + alertIds
        prefs.edit().putStringSet(KEY_SEEN_IDS, updated).apply()
        _seenIds.value = updated
    }

    /**
     * Marks alert IDs as deleted. Deleted alerts are filtered out and will not appear again.
     */
    fun deleteAlerts(alertIds: Collection<String>) {
        if (alertIds.isEmpty()) return
        val updated = _deletedIds.value + alertIds
        prefs.edit().putStringSet(KEY_DELETED_IDS, updated).apply()
        _deletedIds.value = updated
    }

    fun deleteAlert(alertId: String) {
        deleteAlerts(listOf(alertId))
    }
}
