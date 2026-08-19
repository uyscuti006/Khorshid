package com.v2ray.ang.reachability

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.util.UUID

/**
 * Clean IP Generator
 *
 * Generates optimized IP versions of subscription configs
 * using validated IPs from TLS scan results.
 */
object CleanIPGenerator {

    private const val TAG = "CleanIPGenerator"
    private const val PREF_CLEAN_IP_CONFIGS = "pref_clean_ip_configs"
    private const val CLEAN_IP_FLAG = "isCleanIpGenerated=true"
    private const val SOURCE_ID_FLAG = "sourceConfigId="

    /**
     * Generate Clean IP configs from subscription configs + scan results.
     *
     * For each source config:
     * 1. Clone N times with different validated IPs
     * 2. Replace address field with validated IP
     * 3. Keep SNI/Host/port/transport unchanged
     * 4. Mark with isCleanIpGenerated=true flag
     *
     * @param sourceConfigs List of source config GUIDs
     * @param scanResults TLS-validated scan results
     * @param n Number of clones per config (default 3)
     * @return List of generated config GUIDs
     */
    private const val CLEAN_IP_SUB_NAME = "Clean IP Configs"
    private var cleanIpSubGuid: String? = null

    fun generateCleanIPConfigs(
        sourceConfigs: List<String>,
        scanResults: List<IpScannerManager.ScanResult>,
        n: Int = 3
    ): List<String> {
        val generatedGuids = mutableListOf<String>()

        try {
            // Get top IPs by latency (TCP scan results)
            val topIps = scanResults
                .sortedBy { it.latencyMs }
                .take(n.coerceAtMost(scanResults.size))

            if (topIps.isEmpty()) {
                LogUtil.i(TAG, "No IPs available for Clean IP generation")
                return emptyList()
            }

            // Find or create "Clean IP Configs" subscription
            val existingSubs = MmkvManager.decodeSubscriptions()
            val cleanSub = existingSubs.find { it.subscription.remarks == CLEAN_IP_SUB_NAME }
            if (cleanSub != null) {
                cleanIpSubGuid = cleanSub.guid
                // Delete old configs in this subscription
                val oldGuids = MmkvManager.decodeServerList(cleanSub.guid)
                oldGuids.forEach { MmkvManager.removeServer(it) }
            } else {
                // Create new subscription
                val newSub = com.v2ray.ang.dto.entities.SubscriptionItem().apply {
                    url = ""
                    remarks = CLEAN_IP_SUB_NAME
                    enabled = true
                }
                cleanIpSubGuid = UUID.randomUUID().toString()
                MmkvManager.encodeSubscription(cleanIpSubGuid!!, newSub)
            }

            // Generate new configs
            for (sourceGuid in sourceConfigs) {
                val sourceConfig = MmkvManager.decodeServerConfig(sourceGuid)
                if (sourceConfig == null) continue

                for ((index, ipResult) in topIps.withIndex()) {
                    val newConfig = sourceConfig.copy()
                    newConfig.remarks = "${sourceConfig.remarks} [Clean IP] — ${ipResult.ip} (${ipResult.latencyMs}ms)"
                    newConfig.server = ipResult.ip
                    newConfig.serverPort = ipResult.port.toString()
                    newConfig.subscriptionId = cleanIpSubGuid ?: ""
                    // Mark as Clean IP generated
                    val description = sourceConfig.description.orEmpty()
                    val cleanIpMark = "$CLEAN_IP_FLAG|$SOURCE_ID_FLAG$sourceGuid"
                    newConfig.description = if (description.isBlank()) cleanIpMark else "$description|$cleanIpMark"

                    val newGuid = MmkvManager.encodeServerConfig(
                        UUID.randomUUID().toString(),
                        newConfig
                    )
                    if (newGuid != null) {
                        generatedGuids.add(newGuid)
                    }
                }
            }

            // Update subscription server list
            if (cleanIpSubGuid != null && generatedGuids.isNotEmpty()) {
                MmkvManager.encodeServerList(generatedGuids, cleanIpSubGuid!!)
            }

            LogUtil.i(TAG, "Generated ${generatedGuids.size} Clean IP configs in subscription $CLEAN_IP_SUB_NAME")
        } catch (e: Exception) {
            LogUtil.e(TAG, "generateCleanIPConfigs failed", e)
        }

        return generatedGuids
    }

    /**
     * Delete all configs marked as Clean IP generated.
     */
    fun deleteAllCleanIPConfigs() {
        try {
            // Delete configs from Clean IP subscription if exists
            val existingSubs = MmkvManager.decodeSubscriptions()
            val cleanSub = existingSubs.find { it.subscription.remarks == CLEAN_IP_SUB_NAME }
            if (cleanSub != null) {
                val guids = MmkvManager.decodeServerList(cleanSub.guid)
                guids.forEach { MmkvManager.removeServer(it) }
            }
            // Also delete any orphaned configs
            val allGuids = MmkvManager.decodeAllServerList()
            for (guid in allGuids) {
                val config = MmkvManager.decodeServerConfig(guid)
                if (config?.description?.contains(CLEAN_IP_FLAG) == true) {
                    MmkvManager.removeServer(guid)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "deleteAllCleanIPConfigs failed", e)
        }
    }

    /**
     * Check if a config is a Clean IP generated config.
     */
    fun isCleanIPConfig(guid: String): Boolean {
        return try {
            val config = MmkvManager.decodeServerConfig(guid)
            config?.description?.contains(CLEAN_IP_FLAG) == true
        } catch (e: Exception) {
            false
        }
    }
}
