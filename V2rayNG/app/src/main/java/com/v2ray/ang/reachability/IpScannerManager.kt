package com.v2ray.ang.reachability

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object IpScannerManager {

    private const val TAG = "IpScanner"

    // MMKV keys
    private const val PREF_SCAN_RESULTS = "pref_ip_scan_results"
    private const val PREF_OPTIMIZED_IPS = "pref_optimized_ips"

    data class ScanResult(
        val ip: String,
        val port: Int,
        val latencyMs: Long,
        val tlsOk: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Scan state
    private var scanJob: Job? = null
    private val testedCount = AtomicInteger(0)
    private val greenCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)

    var onScanProgress: ((tested: Int, green: Int, failed: Int) -> Unit)? = null
    var onScanComplete: ((List<ScanResult>) -> Unit)? = null

    /**
     * Phase 1: TCP probe to Cloudflare IPs across ALL provided ports concurrently.
     */
    fun startTcpScan(
        ips: List<String>,
        ports: List<Int>,
        timeoutMs: Int = 3000,
        concurrency: Int = 50,
        scope: CoroutineScope
    ) {
        stopScan()
        testedCount.set(0)
        greenCount.set(0)
        failedCount.set(0)

        scanJob = scope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(concurrency)
            val results = ConcurrentLinkedQueue<ScanResult>()

            // ساخت تمام ترکیب‌های ممکن (IP + Port) جهت اسکن کامل
            val scanTasks = ips.flatMap { ip -> ports.map { port -> ip to port } }

            val jobs = scanTasks.map { (ip, port) ->
                async {
                    semaphore.withPermit {
                        if (!isActive) return@async
                        val result = probeSingleTcp(ip, port, timeoutMs)
                        if (result != null) {
                            results.add(result)
                            greenCount.incrementAndGet()
                        } else {
                            failedCount.incrementAndGet()
                        }
                        testedCount.incrementAndGet()
                        onScanProgress?.invoke(testedCount.get(), greenCount.get(), failedCount.get())
                    }
                }
            }

            jobs.awaitAll()
            val sortedResults = results.sortedBy { it.latencyMs }
            onScanComplete?.invoke(sortedResults)
        }
    }

    /**
     * Phase 2: TLS/SNI validation with config-specific port.
     */
    fun startTlsValidation(
        scanResults: List<ScanResult>,
        configPort: Int,
        sni: String,
        topN: Int = 20,
        scope: CoroutineScope
    ) {
        stopScan()
        testedCount.set(0)
        greenCount.set(0)
        failedCount.set(0)

        scanJob = scope.launch(Dispatchers.IO) {
            val topIps = scanResults.take(topN)
            val validatedResults = mutableListOf<ScanResult>()

            for (result in topIps) {
                if (!isActive) break

                val tlsResult = TlsSniProbe.probeWithSni(
                    ip = result.ip,
                    port = configPort,
                    sni = sni
                )

                if (tlsResult.success) {
                    validatedResults.add(
                        result.copy(
                            port = configPort,
                            tlsOk = true,
                            latencyMs = result.latencyMs + tlsResult.handshakeMs
                        )
                    )
                    greenCount.incrementAndGet()
                } else {
                    failedCount.incrementAndGet()
                }
                testedCount.incrementAndGet()
                onScanProgress?.invoke(testedCount.get(), greenCount.get(), failedCount.get())
            }

            onScanComplete?.invoke(validatedResults)
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun isScanning(): Boolean = scanJob?.isActive == true

    fun getCachedResults(): List<ScanResult> {
        return try {
            val lastSessionId = IpScanPersistence.getLastSessionId() ?: return emptyList()
            IpScanPersistence.loadResults(lastSessionId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Per-config optimized IPs ──────────────────────────────────────

    data class OptimizedIps(
        val configGuid: String,
        val ips: List<ScanResult>,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun saveOptimizedIps(configGuid: String, ips: List<ScanResult>) {
        try {
            val top5 = ips.take(5)
            val json = JsonUtil.toJson(top5)
            MmkvManager.encodeSettings("${PREF_OPTIMIZED_IPS}_$configGuid", json)
            LogUtil.i(TAG, "Saved ${top5.size} optimized IPs for config=$configGuid")
        } catch (e: Exception) {
            LogUtil.e(TAG, "saveOptimizedIps failed", e)
        }
    }

    fun getOptimizedIps(configGuid: String): List<ScanResult> {
        return try {
            val json = MmkvManager.decodeSettingsString("${PREF_OPTIMIZED_IPS}_$configGuid")
                ?: return emptyList()
            JsonUtil.fromJsonSafe(json, Array<ScanResult>::class.java)?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            LogUtil.e(TAG, "getOptimizedIps failed", e)
            emptyList()
        }
    }

    fun clearOptimizedIps(configGuid: String) {
        MmkvManager.encodeSettings("${PREF_OPTIMIZED_IPS}_$configGuid", "")
    }

    /**
     * Probe a single IP on a specific port.
     */
    private suspend fun probeSingleTcp(
        ip: String,
        port: Int,
        timeoutMs: Int
    ): ScanResult? = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            val startTime = System.currentTimeMillis()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            val latency = System.currentTimeMillis() - startTime
            socket.close()
            ScanResult(ip = ip, port = port, latencyMs = latency)
        } catch (_: Exception) {
            null
        }
    }
}