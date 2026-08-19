package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kill Switch Manager
 *
 * When VPN drops unexpectedly, keeps the tun interface open to block all traffic.
 * Retries reconnection periodically. If all retries fail, notifies user.
 *
 * State machine:
 *   CONNECTED → BLOCKING (if kill switch ON and VPN drops)
 *   BLOCKING → CONNECTING (retry every 30s, max 5 times)
 *   BLOCKING → DISCONNECTED (user disables kill switch or max retries reached)
 */
object KillSwitchManager {

    private const val TAG = "KillSwitch"

    private const val DEFAULT_RECONNECT_MS = 30_000L  // 30 seconds
    private const val DEFAULT_MAX_RETRIES = 5

    private var retryJob: Job? = null
    private var retryCount = 0

    var onReconnectNeeded: (() -> Unit)? = null
    var onMaxRetriesReached: (() -> Unit)? = null

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_KILL_SWITCH_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_KILL_SWITCH_ENABLED, enabled)
        if (!enabled) cancelRetry()
    }

    fun getReconnectInterval(): Long =
        MmkvManager.decodeSettingsString(AppConfig.PREF_KILL_SWITCH_RECONNECT_MS, DEFAULT_RECONNECT_MS.toString())?.toLongOrNull() ?: DEFAULT_RECONNECT_MS

    fun getMaxRetries(): Int =
        MmkvManager.decodeSettingsString(AppConfig.PREF_KILL_SWITCH_MAX_RETRIES, DEFAULT_MAX_RETRIES.toString())?.toIntOrNull() ?: DEFAULT_MAX_RETRIES

    /**
     * Called when VPN drops while Kill Switch is active.
     * Starts retry loop to reconnect.
     */
    fun enterBlockingMode(scope: CoroutineScope) {
        if (!isEnabled()) return
        retryCount = 0
        retryJob?.cancel()
        retryJob = scope.launch(Dispatchers.IO) {
            while (retryCount < getMaxRetries()) {
                delay(getReconnectInterval())
                retryCount++
                LogUtil.i(TAG, "Kill switch retry $retryCount/${getMaxRetries()}")
                onReconnectNeeded?.invoke()
            }
            LogUtil.w(TAG, "Max retries reached ($retryCount)")
            onMaxRetriesReached?.invoke()
        }
    }

    /**
     * Called when connection is restored (StateStartSuccess).
     * Resets retry counter.
     */
    fun exitBlockingMode() {
        cancelRetry()
        retryCount = 0
    }

    /**
     * Called when user manually disables kill switch.
     */
    fun disable() {
        setEnabled(false)
        cancelRetry()
    }

    fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    fun reset() {
        cancelRetry()
        retryCount = 0
    }
}
