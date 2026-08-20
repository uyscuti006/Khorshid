package com.v2ray.ang.reachability

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil

typealias CleanIPGenerator = KhorshidConfigGenerator

object IpScannerManager {
    private const val PREF_OPTIMIZED_IPS = "pref_optimized_ips"

    fun getOptimizedIps(configGuid: String): List<KhorshidScanEngine.EndpointResult> {
        return try {
            val json = MmkvManager.decodeSettingsString("${PREF_OPTIMIZED_IPS}_$configGuid") ?: return emptyList()
            JsonUtil.fromJsonSafe(json, Array<KhorshidScanEngine.EndpointResult>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveOptimizedIps(configGuid: String, ips: List<KhorshidScanEngine.EndpointResult>) {
        try {
            val top5 = ips.filter { it.isHealthy }.take(5)
            val json = JsonUtil.toJson(top5)
            MmkvManager.encodeSettings("${PREF_OPTIMIZED_IPS}_$configGuid", json)
        } catch (_: Exception) {}
    }

    fun clearOptimizedIps(configGuid: String) {
        MmkvManager.encodeSettings("${PREF_OPTIMIZED_IPS}_$configGuid", "")
    }
}
