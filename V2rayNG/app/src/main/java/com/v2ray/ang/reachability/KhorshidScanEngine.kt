package com.v2ray.ang.reachability

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object KhorshidScanEngine {

    data class EndpointResult(
        val ip: String,
        val port: Int,
        val latencyMs: Long,
        val colo: String? = null,
        val speedMbps: Double = 0.0,
        val isHealthy: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ScanEvent(
        val tested: Int,
        val green: Int,
        val failed: Int,
        val total: Int,
        val latestHealthy: EndpointResult? = null
    )

    private var scanJob: Job? = null
    private val _eventFlow = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 128)
    val eventFlow: SharedFlow<ScanEvent> = _eventFlow.asSharedFlow()

    private val testedCount = AtomicInteger(0)
    private val greenCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    // صف ذخیره زنده آی‌پی‌های سالم برای دسترسی در صورت توقف زودهنگام
    private val currentHealthyResults = ConcurrentLinkedQueue<EndpointResult>()

    /**
     * دریافت آی‌پی‌های سالمی که تا این لحظه کشف شده‌اند (برای حالت توقف اضطراری)
     */
    fun getDiscoveredHealthyResults(): List<EndpointResult> {
        return currentHealthyResults.toList().sortedBy { it.latencyMs }
    }

    fun startDiscovery(
        ips: List<String>,
        ports: List<Int>,
        hostHeader: String = KhorshidConstants.DEFAULT_CF_HOST,
        workers: Int = 50,
        timeoutMs: Int = 3000,
        requireWebSocket: Boolean = false,
        autoScanNeighbors: Boolean = false,
        scope: CoroutineScope,
        onComplete: (List<EndpointResult>) -> Unit
    ) {
        stopDiscovery()
        testedCount.set(0)
        greenCount.set(0)
        failedCount.set(0)
        totalCount.set(0)
        currentHealthyResults.clear()

        scanJob = scope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(workers.coerceIn(10, 300))
            val visitedEndpoints = ConcurrentHashMap.newKeySet<String>()

            val taskQueue = ConcurrentLinkedQueue<Pair<String, Int>>()
            ips.forEach { ip ->
                ports.forEach { port ->
                    taskQueue.add(ip to port)
                    visitedEndpoints.add("$ip:$port")
                }
            }

            totalCount.set(taskQueue.size)
            val activeJobs = ConcurrentLinkedQueue<Job>()

            while (isActive && !taskQueue.isEmpty()) {
                val task = taskQueue.poll() ?: break
                val (ip, port) = task

                val job = launch {
                    semaphore.withPermit {
                        if (!isActive) return@withPermit

                        val probe = KhorshidProber.probeEndpoint(ip, port, hostHeader, timeoutMs, requireWebSocket)
                        if (probe.success) {
                            val res = EndpointResult(
                                ip = ip,
                                port = port,
                                latencyMs = probe.ttfbMs,
                                colo = probe.colo,
                                isHealthy = true
                            )
                            currentHealthyResults.add(res)
                            greenCount.incrementAndGet()

                            if (autoScanNeighbors) {
                                val neighbors = KhorshidNeighborScanner.getSubnet24Neighbors(ip)
                                neighbors.take(30).forEach { neighborIp ->
                                    val key = "$neighborIp:$port"
                                    if (visitedEndpoints.add(key)) {
                                        taskQueue.add(neighborIp to port)
                                        totalCount.incrementAndGet()
                                    }
                                }
                            }

                            _eventFlow.tryEmit(ScanEvent(testedCount.incrementAndGet(), greenCount.get(), failedCount.get(), totalCount.get(), res))
                        } else {
                            failedCount.incrementAndGet()
                            _eventFlow.tryEmit(ScanEvent(testedCount.incrementAndGet(), greenCount.get(), failedCount.get(), totalCount.get(), null))
                        }
                    }
                }
                activeJobs.add(job)
            }

            activeJobs.forEach { it.join() }
            val sorted = currentHealthyResults.toList().sortedBy { it.latencyMs }
            onComplete(sorted)
        }
    }

    fun runSpeedTestShortlist(
        allResults: List<EndpointResult>,
        topCount: Int = 20,
        hostHeader: String = KhorshidConstants.DEFAULT_CF_HOST,
        concurrency: Int = 4,
        scope: CoroutineScope,
        onUpdate: (List<EndpointResult>) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val toTest = allResults.take(topCount)
            val rest = allResults.drop(topCount)

            val semaphore = Semaphore(concurrency)
            val testedList = ConcurrentLinkedQueue<EndpointResult>()

            val jobs = toTest.map { endpoint ->
                async {
                    semaphore.withPermit {
                        if (!isActive) {
                            testedList.add(endpoint)
                            return@withPermit
                        }
                        val speedRes = KhorshidSpeedTester.testDownload(endpoint.ip, endpoint.port, hostHeader)
                        val speed = if (speedRes.success) speedRes.speedMbps else 0.0
                        testedList.add(endpoint.copy(speedMbps = speed))
                    }
                }
            }

            jobs.awaitAll()
            val fullMergedList = (testedList.toList() + rest).sortedWith(
                compareByDescending<EndpointResult> { it.speedMbps }.thenBy { it.latencyMs }
            )
            onUpdate(fullMergedList)
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
    }

    fun isRunning(): Boolean = scanJob?.isActive == true
}