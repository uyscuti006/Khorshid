package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Auto-failover manager.
 *
 * Monitors VPN tunnel health via SOCKS proxy and automatically switches
 * to the next best server when the current one becomes unhealthy.
 *
 * Health check routes through v2ray's local SOCKS proxy to ensure the
 * test actually verifies the VPN tunnel, not the direct network.
 */
object AutoFailoverManager {

    private const val TAG = "AutoFailover"

    // ── Settings ──────────────────────────────────────────────────────
    private const val HEALTH_CHECK_URL = "http://connectivitycheck.gstatic.com/generate_204"
    private const val HEALTH_CHECK_TIMEOUT_MS = 5000
    private const val CONSECUTIVE_FAILURES_THRESHOLD = 3
    private const val DEFAULT_INTERVAL_MS = 15_000L
    private const val DEFAULT_MAX_FAILOVERS = 5
    private const val DEFAULT_BLACKLIST_MS = 300_000L // 5 minutes
    private const val FAILOVER_WINDOW_MS = 600_000L  // 10 minutes

    // ── State ─────────────────────────────────────────────────────────
    private val blacklistedServers = mutableMapOf<String, Long>() // guid -> expiry
    private var failoverCount = 0
    private var failoverWindowStart = 0L
    private var healthCheckJob: Job? = null
    private var consecutiveFailures = 0

    // Callback for triggering actual VPN switch
    var onFailoverNeeded: (() -> Unit)? = null

    // ── Settings Access ───────────────────────────────────────────────
    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_FAILOVER_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_FAILOVER_ENABLED, enabled)
        if (!enabled) cancelHealthCheck()
    }

    fun getHealthCheckInterval(): Long =
        MmkvManager.decodeSettingsString(AppConfig.PREF_FAILOVER_INTERVAL_MS, DEFAULT_INTERVAL_MS.toString())?.toLongOrNull() ?: DEFAULT_INTERVAL_MS

    fun getMaxFailovers(): Int =
        MmkvManager.decodeSettingsString(AppConfig.PREF_FAILOVER_MAX, DEFAULT_MAX_FAILOVERS.toString())?.toIntOrNull() ?: DEFAULT_MAX_FAILOVERS

    fun getBlacklistDuration(): Long =
        MmkvManager.decodeSettingsString(AppConfig.PREF_FAILOVER_BLACKLIST_MS, DEFAULT_BLACKLIST_MS.toString())?.toLongOrNull() ?: DEFAULT_BLACKLIST_MS

    // ── Health Check Lifecycle ────────────────────────────────────────
    fun startHealthCheck(scope: CoroutineScope) {
        if (!isEnabled()) return
        cancelHealthCheck()
        consecutiveFailures = 0
        healthCheckJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(getHealthCheckInterval())
                try {
                    val healthy = performHealthCheck()
                    if (healthy) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures++
                        LogUtil.w(TAG, "Health check failed ($consecutiveFailures/$CONSECUTIVE_FAILURES_THRESHOLD)")
                        if (consecutiveFailures >= CONSECUTIVE_FAILURES_THRESHOLD) {
                            LogUtil.w(TAG, "Unhealthy detected — triggering failover")
                            triggerFailover()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Health check error", e)
                    consecutiveFailures++
                    if (consecutiveFailures >= CONSECUTIVE_FAILURES_THRESHOLD) {
                        triggerFailover()
                    }
                }
            }
        }
    }

    fun cancelHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        consecutiveFailures = 0
        LogUtil.i(TAG, "Health check cancelled")
    }

    // ── Health Check via SOCKS Proxy ──────────────────────────────────
    private fun performHealthCheck(): Boolean {
        val socksPort = MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT, "10808")?.toIntOrNull() ?: 10808
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))

        return try {
            val url = URL(HEALTH_CHECK_URL)
            val conn = (url.openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS
                readTimeout = HEALTH_CHECK_TIMEOUT_MS
                requestMethod = "HEAD"
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            conn.disconnect()
            code == 204 || code == 200
        } catch (e: Exception) {
            false
        }
    }

    // ── Failover Logic ────────────────────────────────────────────────
    fun triggerFailover() {
        if (!isEnabled()) return

        // Check rate limit
        val now = System.currentTimeMillis()
        if (now - failoverWindowStart > FAILOVER_WINDOW_MS) {
            failoverCount = 0
            failoverWindowStart = now
        }
        if (failoverCount >= getMaxFailovers()) {
            LogUtil.w(TAG, "Failover limit reached (${failoverCount}/${getMaxFailovers()})")
            // Let the caller handle the notification
            onFailoverNeeded?.invoke()
            return
        }

        // Clean expired blacklist entries
        blacklistedServers.entries.removeAll { it.value < now }

        // Find next server
        val currentGuid = MmkvManager.getSelectServer() ?: return
        val currentConfig = MmkvManager.decodeServerConfig(currentGuid)
        val groupId = currentConfig?.subscriptionId ?: return

        val allGuids = MmkvManager.decodeServerList(groupId)
        val candidates = allGuids.filter { guid ->
            guid != currentGuid && !blacklistedServers.containsKey(guid)
        }

        if (candidates.isEmpty()) {
            LogUtil.w(TAG, "No alternative servers available in group $groupId")
            onFailoverNeeded?.invoke()
            return
        }

        // Pick best candidate by test delay
        val candidatesWithDelay = candidates.mapNotNull { guid ->
            val affiliation = MmkvManager.decodeServerAffiliationInfo(guid)
            val delay = affiliation?.testDelayMillis ?: 0L
            if (delay > 0) Pair(guid, delay) else null
        }.sortedBy { it.second }

        val bestGuid = if (candidatesWithDelay.isNotEmpty()) {
            candidatesWithDelay[0].component1()
        } else {
            candidates[0]
        }

        // Blacklist current server
        blacklistedServers[currentGuid] = now + getBlacklistDuration()
        failoverCount++

        LogUtil.i(TAG, "Failover: $currentGuid -> $bestGuid (attempt=$failoverCount)")

        // Switch server
        MmkvManager.setSelectServer(bestGuid)
        onFailoverNeeded?.invoke()
    }

    // ── Immediate Failover (on service disconnect) ────────────────────
    fun triggerImmediateFailover() {
        if (!isEnabled()) return
        cancelHealthCheck()
        triggerFailover()
    }

    // ── Cleanup ───────────────────────────────────────────────────────
    fun reset() {
        cancelHealthCheck()
        blacklistedServers.clear()
        failoverCount = 0
        failoverWindowStart = 0L
        consecutiveFailures = 0
    }
}
