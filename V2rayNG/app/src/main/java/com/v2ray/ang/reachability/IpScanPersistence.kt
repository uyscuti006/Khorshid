package com.v2ray.ang.reachability

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil

/**
 * MMKV-based persistence for IP scan results.
 */
object IpScanPersistence {
    private const val PREF_SCAN_RESULTS = "pref_ip_scan_results"
    private const val PREF_LAST_SESSION = "pref_ip_scan_last_session"

    fun saveResults(sessionId: String, results: List<KhorshidScanEngine.EndpointResult>) {
        try {
            val json = JsonUtil.toJson(results)
            MmkvManager.encodeSettings("${PREF_SCAN_RESULTS}_$sessionId", json)
            MmkvManager.encodeSettings(PREF_LAST_SESSION, sessionId)
        } catch (_: Exception) {
        }
    }

    fun loadResults(sessionId: String): List<KhorshidScanEngine.EndpointResult> {
        return try {
            val json = MmkvManager.decodeSettingsString("${PREF_SCAN_RESULTS}_$sessionId")
                ?: return emptyList()
            JsonUtil.fromJsonSafe(json, Array<KhorshidScanEngine.EndpointResult>::class.java)?.toList()
                ?: emptyList()
        } catch (_: Exception) {
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
