package com.v2ray.ang.reachability

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object KhorshidSpeedTester {

    private const val SPEED_TIMEOUT_MS = 5000

    data class SpeedResult(
        val success: Boolean,
        val speedMbps: Double = 0.0,
        val durationMs: Long = 0L
    )

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    suspend fun testDownload(
        ip: String,
        port: Int,
        host: String = KhorshidConstants.DEFAULT_CF_HOST
    ): SpeedResult = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null

        try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(ip, port), SPEED_TIMEOUT_MS)

            sslSocket = (sslContext.socketFactory.createSocket(rawSocket, ip, port, true) as SSLSocket).apply {
                soTimeout = SPEED_TIMEOUT_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && host.isNotBlank()) {
                    try {
                        val params = sslParameters
                        params.serverNames = listOf(SNIHostName(host))
                        sslParameters = params
                    } catch (_: Exception) {}
                }
                enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2").filter { it in supportedProtocols }.toTypedArray()
            }

            sslSocket.startHandshake()

            val writer = OutputStreamWriter(sslSocket.outputStream, "UTF-8")
            val req = "GET ${KhorshidConstants.SPEED_TEST_PATH} HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "User-Agent: KhorshidSpeed/1.0\r\n" +
                    "Connection: close\r\n\r\n"
            writer.write(req)
            writer.flush()

            val start = System.currentTimeMillis()
            val input: InputStream = sslSocket.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                totalBytes += bytesRead
                if (System.currentTimeMillis() - start > SPEED_TIMEOUT_MS) break
            }

            val duration = (System.currentTimeMillis() - start).coerceAtLeast(1)
            val mbps = (totalBytes * 8.0 / (duration / 1000.0)) / (1024 * 1024)

            SpeedResult(
                success = totalBytes > 1024,
                speedMbps = String.format("%.2f", mbps).toDouble(),
                durationMs = duration
            )
        } catch (_: Exception) {
            SpeedResult(success = false)
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }
}
