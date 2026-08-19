package com.v2ray.ang.reachability

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil

/**
 * MMKV-based persistence for IP scan results.
 * Replaces Room to avoid adding new dependencies.
 */
object IpScanPersistence {
    private const val PREF_SCAN_RESULTS = "pref_ip_scan_results"
    private const val PREF_LAST_SESSION = "pref_ip_scan_last_session"

    fun saveResults(sessionId: String, results: List<IpScannerManager.ScanResult>) {
        try {
            val json = JsonUtil.toJson(results)
            MmkvManager.encodeSettings("${PREF_SCAN_RESULTS}_$sessionId", json)
            MmkvManager.encodeSettings(PREF_LAST_SESSION, sessionId)
        } catch (e: Exception) {
            // Silent fail
        }
    }

    fun loadResults(sessionId: String): List<IpScannerManager.ScanResult> {
        return try {
            val json = MmkvManager.decodeSettingsString("${PREF_SCAN_RESULTS}_$sessionId")
                ?: return emptyList()
            JsonUtil.fromJsonSafe(json, Array<IpScannerManager.ScanResult>::class.java)?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLastSessionId(): String? {
        return MmkvManager.decodeSettingsString(PREF_LAST_SESSION)
    }

    fun clearAll() {
        val lastSession = getLastSessionId()
        if (lastSession != null) {
            MmkvManager.encodeSettings("${PREF_SCAN_RESULTS}_$lastSession", "")
        }
        MmkvManager.encodeSettings(PREF_LAST_SESSION, "")
    }
}
