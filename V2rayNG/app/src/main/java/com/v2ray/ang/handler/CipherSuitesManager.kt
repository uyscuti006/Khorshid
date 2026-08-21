package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil

/**
 * CipherSuites Manager
 *
 * Handles parsing #SPEEDBOOST line from subscription content,
 * applying fingerPrint/cipherSuites/finalMask to the fastest server before connecting,
 * and restoring original values after disconnecting.
 *
 * Safety rules:
 * - All parsing is try/catch, never crashes
 * - Missing #SPEEDBOOST line → silent no-op
 * - Per-guid backup (not global) to prevent cross-server contamination
 * - applyBeforeConnect is synchronous (waits for encodeServerConfig)
 * - restoreAfterDisconnect is idempotent
 * - startupSafetyCheck restores any orphaned backups
 */
object CipherSuitesManager {

    private const val TAG = "CipherSuitesManager"

    // MMKV keys
    private const val PREF_CIPHERSUITES_ENABLED = "pref_ciphersuites_enabled"
    private const val PREF_CIPHERSUITES_CACHE = "pref_ciphersuites_cache"
    private const val BACKUP_KEY_PREFIX = "ciphersuites_backup_"

    data class CipherSuitesProfile(
        val version: Int,
        val fingerPrint: String,
        val cipherSuites: String,
        val finalMask: String
    )

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Check if CipherSuites is enabled by user.
     */
    fun isEnabled(): Boolean {
        return try {
            MmkvManager.decodeSettingsBool(PREF_CIPHERSUITES_ENABLED, false)
        } catch (e: Exception) {
            LogUtil.e(TAG, "isEnabled failed", e)
            false
        }
    }

    /**
     * Enable/disable CipherSuites.
     */
    fun setEnabled(enabled: Boolean) {
        try {
            MmkvManager.encodeSettings(PREF_CIPHERSUITES_ENABLED, enabled)
        } catch (e: Exception) {
            LogUtil.e(TAG, "setEnabled failed", e)
        }
    }

    /**
     * Parse #SPEEDBOOST line from decoded subscription content.
     *
     * Format: #SPEEDBOOST|v=<version>|fp=<fingerPrint>|cs=<cipherSuites>|fm=<finalMask JSON>
     *
     * Important: fm= is ALWAYS the last segment. Everything after "|fm=" until
     * end of line is the finalMask value (which is JSON and must NOT be split by "|").
     *
     * @param rawFileContent Base64-decoded subscription content (full text)
     * @return CipherSuitesProfile if parsing succeeds, null otherwise
     */
    fun parseSpeedBoostLine(rawFileContent: String): CipherSuitesProfile? {
        return try {
            val lines = rawFileContent.lines()
            val boostLine = lines.firstOrNull { it.trimStart().startsWith("#SPEEDBOOST|") }
                ?: return null

            val trimmed = boostLine.trim()

            // Find fm= position - everything after this is finalMask
            val fmIndex = trimmed.indexOf("|fm=")
            if (fmIndex < 0) {
                LogUtil.i(TAG, "#SPEEDBOOST line missing fm= section")
                return null
            }

            // Extract fm value: everything after "|fm=" to end of line
            val fmValue = trimmed.substring(fmIndex + 4).trim()
            if (fmValue.isBlank()) {
                LogUtil.i(TAG, "#SPEEDBOOST fm= is empty")
                return null
            }

            // Parse v=, fp=, cs= from the part before fm=
            val prefix = trimmed.substring(0, fmIndex)
            val parts = prefix.split("|")

            var version: Int? = null
            var fingerPrint: String? = null
            var cipherSuites: String? = null

            for (part in parts) {
                val trimmedPart = part.trim()
                when {
                    trimmedPart.startsWith("v=") -> {
                        version = trimmedPart.substring(2).trim().toIntOrNull()
                    }
                    trimmedPart.startsWith("fp=") -> {
                        fingerPrint = trimmedPart.substring(3).trim()
                    }
                    trimmedPart.startsWith("cs=") -> {
                        cipherSuites = trimmedPart.substring(3).trim()
                    }
                }
            }

            // Validate all required fields present
            if (version == null || fingerPrint.isNullOrBlank() || cipherSuites.isNullOrBlank()) {
                LogUtil.i(TAG, "#SPEEDBOOST has missing fields: v=$version, fp=$fingerPrint, cs=${cipherSuites != null}")
                return null
            }

            CipherSuitesProfile(
                version = version,
                fingerPrint = fingerPrint,
                cipherSuites = cipherSuites,
                finalMask = fmValue
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "parseSpeedBoostLine failed", e)
            null
        }
    }

    /**
     * Get cached CipherSuitesProfile from MMKV.
     */
    fun getCachedProfile(): CipherSuitesProfile? {
        return try {
            val json = MmkvManager.decodeSettingsString(PREF_CIPHERSUITES_CACHE) ?: return null
            JsonUtil.fromJsonSafe(json, CipherSuitesProfile::class.java)
        } catch (e: Exception) {
            LogUtil.e(TAG, "getCachedProfile failed", e)
            null
        }
    }

    /**
     * Cache CipherSuitesProfile to MMKV.
     */
    fun cacheProfile(profile: CipherSuitesProfile) {
        try {
            val json = JsonUtil.toJson(profile)
            MmkvManager.encodeSettings(PREF_CIPHERSUITES_CACHE, json)
        } catch (e: Exception) {
            LogUtil.e(TAG, "cacheProfile failed", e)
        }
    }

    /**
     * Apply CipherSuites values to the server before connecting.
     *
     * This method is SYNCHRONOUS - it waits for encodeServerConfig to complete
     * before returning, ensuring the service starts with modified config.
     *
     * Rules:
     * - Only runs if enabled AND cached profile exists
     * - Backs up original values ONLY if no backup exists for this guid (never overwrites backup)
     * - Applies CipherSuites values to ProfileItem and saves
     *
     * @param guid Server GUID to apply CipherSuites to
     * @param forceDisable If true, skip applying even if enabled (use when connecting from Advanced screen)
     */
    fun applyBeforeConnect(guid: String, forceDisable: Boolean = false) {
        try {
            if (forceDisable || !isEnabled()) return
            val profile = getCachedProfile() ?: return

            val config = MmkvManager.decodeServerConfig(guid)
            if (config == null) {
                LogUtil.e(TAG, "applyBeforeConnect: config not found for guid=$guid")
                return
            }

            val backupKey = BACKUP_KEY_PREFIX + guid

            // Only backup if no backup exists (never overwrite user's original values)
            if (MmkvManager.decodeSettingsString(backupKey).isNullOrBlank()) {
                val backup = BackupData(
                    fingerPrint = config.fingerPrint,
                    cipherSuites = config.cipherSuites,
                    finalMask = config.finalMask
                )
                MmkvManager.encodeSettings(backupKey, JsonUtil.toJson(backup))
                LogUtil.i(TAG, "Backup created for guid=$guid")
            }

            // Apply CipherSuites values
            config.fingerPrint = profile.fingerPrint
            config.cipherSuites = profile.cipherSuites
            config.finalMask = profile.finalMask

            // Save synchronously - this MUST complete before service starts
            MmkvManager.encodeServerConfig(guid, config)
            LogUtil.i(TAG, "CipherSuites applied to guid=$guid: fp=${profile.fingerPrint}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "applyBeforeConnect failed for guid=$guid", e)
        }
    }

    /**
     * Apply CipherSuites values to all servers before ping testing.
     *
     * @param guids List of server GUIDs to apply to
     */
    fun applyToAll(guids: List<String>) {
        if (!isEnabled()) return
        val profile = getCachedProfile() ?: return
        for (guid in guids) {
            applyBeforeConnect(guid)
        }
    }

    /**
     * Restore original values for all servers except the selected one.
     *
     * @param guids All server GUIDs that were modified
     * @param exceptGuid The GUID to keep modified (the one we're connecting to)
     */
    fun restoreAllExcept(guids: List<String>, exceptGuid: String) {
        for (guid in guids) {
            if (guid != exceptGuid) {
                restoreAfterDisconnect(guid)
            }
        }
    }

    /**
     * Restore original values after disconnect.
     *
     * Idempotent - calling multiple times is safe.
     *
     * @param guid Server GUID to restore
     */
    fun restoreAfterDisconnect(guid: String) {
        try {
            val backupKey = BACKUP_KEY_PREFIX + guid
            val backupJson = MmkvManager.decodeSettingsString(backupKey) ?: return

            val backup = JsonUtil.fromJsonSafe(backupJson, BackupData::class.java) ?: return

            val config = MmkvManager.decodeServerConfig(guid) ?: run {
                // Config gone, just clean up backup
                MmkvManager.encodeSettings(backupKey, "")
                return
            }

            // Restore original values
            config.fingerPrint = backup.fingerPrint
            config.cipherSuites = backup.cipherSuites
            config.finalMask = backup.finalMask

            MmkvManager.encodeServerConfig(guid, config)

            // Clean up backup
            MmkvManager.encodeSettings(backupKey, "")
            LogUtil.i(TAG, "Original values restored for guid=$guid")
        } catch (e: Exception) {
            LogUtil.e(TAG, "restoreAfterDisconnect failed for guid=$guid", e)
        }
    }

    /**
     * Startup safety check.
     *
     * If VPN service is NOT running but orphaned backups exist (e.g., from a crash),
     * restore them immediately so the user's config stays clean.
     */
    fun startupSafetyCheck() {
        try {
            // Only restore if service is not running
            if (isServiceRunning()) return

            val prefix = BACKUP_KEY_PREFIX
            // Check all known server GUIDs for orphaned backups
            val allServers = MmkvManager.decodeAllServerList()
            for (guid in allServers) {
                val backupKey = prefix + guid
                val backupJson = MmkvManager.decodeSettingsString(backupKey)
                if (!backupJson.isNullOrBlank()) {
                    LogUtil.i(TAG, "Startup safety: restoring orphaned backup for guid=$guid")
                    restoreAfterDisconnect(guid)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "startupSafetyCheck failed", e)
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Check if VPN service is currently running.
     */
    private fun isServiceRunning(): Boolean {
        return try {
            CoreServiceManager.isRunning()
        } catch (e: Exception) {
            // If we can't determine, assume not running to be safe
            false
        }
    }

    /**
     * Backup data structure for per-guid storage.
     */
    data class BackupData(
        val fingerPrint: String?,
        val cipherSuites: String?,
        val finalMask: String?
    )
}
