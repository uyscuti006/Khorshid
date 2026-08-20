package com.v2ray.ang.reachability

import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

object KhorshidConfigGenerator {

    private const val TAG = "KhorshidGenerator"
    const val CLEAN_IP_GROUP_NAME = "Clean IP Configs"
    const val CLEAN_FLAG = "isKhorshidClean=true"
    const val CLEAN_TAG_PREFIX = "[Clean IP]"

    private val generationMutex = Mutex()

    suspend fun cloneConfigsWithCleanIps(
        selectedGuids: List<String>,
        cleanEndpoints: List<KhorshidScanEngine.EndpointResult>,
        ipsPerConfig: Int
    ): Int = generationMutex.withLock {
        var generatedCount = 0

        try {
            val bestEndpoints = cleanEndpoints
                .filter { it.isHealthy }
                .distinctBy { it.ip to it.port }
                .sortedBy { it.latencyMs }
                .take(ipsPerConfig.coerceAtLeast(1))

            if (bestEndpoints.isEmpty() || selectedGuids.isEmpty()) {
                LogUtil.w(TAG, "No valid endpoints or selected configs")
                return@withLock 0
            }

            val groupGuid = cleanupAndFindGroup()
            val oldGuids = MmkvManager.decodeServerList(groupGuid)

            val newGuids = ArrayList<String>()

            for (srcGuid in selectedGuids.distinct()) {
                val source = MmkvManager.decodeServerConfig(srcGuid) ?: continue

                for (endpoint in bestEndpoints) {
                    try {
                        val cfg = MmkvManager.decodeServerConfig(srcGuid) ?: continue

                        cfg.server = endpoint.ip
                        cfg.serverPort = endpoint.port.toString()

                        val coloTag = endpoint.colo?.let { " ($it)" } ?: ""
                        val latencyTag = if (endpoint.latencyMs > 0) " - ${endpoint.latencyMs}ms" else ""
                        val speedTag = if (endpoint.speedMbps > 0) " | ${endpoint.speedMbps}Mbps" else ""

                        val cleanRemarks = source.remarks.orEmpty()
                            .removePrefix(CLEAN_TAG_PREFIX)
                            .trim()

                        cfg.remarks = "$CLEAN_TAG_PREFIX $cleanRemarks | ${endpoint.ip}:${endpoint.port}$latencyTag$coloTag$speedTag"
                        cfg.subscriptionId = groupGuid

                        val currentDesc = source.description.orEmpty()
                        cfg.description = if (currentDesc.isBlank()) CLEAN_FLAG else "$currentDesc|$CLEAN_FLAG"

                        val newGuid = UUID.randomUUID().toString()
                        MmkvManager.encodeServerConfig(newGuid, cfg)
                        newGuids.add(newGuid)
                        generatedCount++
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error creating config for $srcGuid", e)
                    }
                }
            }

            if (newGuids.isEmpty()) {
                LogUtil.w(TAG, "All config generation attempts failed")
                return@withLock 0
            }

            for (oldGuid in oldGuids) {
                try {
                    MmkvManager.removeServer(oldGuid)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error removing old config $oldGuid", e)
                }
            }

            MmkvManager.encodeServerList(ArrayList(newGuids.distinct()), groupGuid)
            LogUtil.i(TAG, "Generated $generatedCount configs in $CLEAN_IP_GROUP_NAME")

        } catch (e: Exception) {
            LogUtil.e(TAG, "cloneConfigsWithCleanIps failed", e)
        }

        generatedCount
    }

    fun cleanupAndFindGroup(): String {
        val existingSubs = try {
            MmkvManager.decodeSubscriptions()
        } catch (_: Exception) {
            emptyList()
        }
        val matches = existingSubs.filter { it.subscription?.remarks == CLEAN_IP_GROUP_NAME }

        val keepGuid: String
        if (matches.isNotEmpty()) {
            keepGuid = matches.first().guid

            for (dup in matches.drop(1)) {
                try {
                    val dupServerGuids = MmkvManager.decodeServerList(dup.guid)
                    for (serverGuid in dupServerGuids) {
                        val cfg = MmkvManager.decodeServerConfig(serverGuid) ?: continue
                        cfg.subscriptionId = keepGuid
                        MmkvManager.encodeServerConfig(serverGuid, cfg)
                    }
                    MmkvManager.removeSubscription(dup.guid)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error migrating duplicate subscription ${dup.guid}", e)
                }
            }
        } else {
            keepGuid = UUID.randomUUID().toString()
            val newSub = SubscriptionItem().apply {
                remarks = CLEAN_IP_GROUP_NAME
                url = ""
                enabled = true
            }
            MmkvManager.encodeSubscription(keepGuid, newSub)
            MmkvManager.encodeServerList(ArrayList<String>(), keepGuid)

            val subsList = MmkvManager.decodeSubsList().toMutableList()
            if (!subsList.contains(keepGuid)) {
                subsList.add(keepGuid)
                MmkvManager.encodeSubsList(subsList)
            }
        }

        return keepGuid
    }

    fun isCleanIPConfig(guid: String): Boolean {
        return try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return false
            config.remarks.orEmpty().contains(CLEAN_TAG_PREFIX, ignoreCase = true) ||
                    config.description?.contains(CLEAN_FLAG) == true ||
                    config.description?.contains("isCleanIpGenerated=true") == true
        } catch (_: Exception) {
            false
        }
    }

    fun clearAllCleanConfigs() {
        try {
            val existingSubs = MmkvManager.decodeSubscriptions()
            for (cache in existingSubs) {
                if (cache.subscription?.remarks == CLEAN_IP_GROUP_NAME) {
                    val serverGuids = MmkvManager.decodeServerList(cache.guid)
                    serverGuids.forEach { MmkvManager.removeServer(it) }
                    MmkvManager.encodeServerList(ArrayList<String>(), cache.guid)
                    break
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "clearAllCleanConfigs failed", e)
        }
    }

    fun deleteAllCleanIPConfigs() {
        clearAllCleanConfigs()
    }
}