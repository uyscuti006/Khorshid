package com.v2ray.ang.reachability

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object KhorshidProber {

    data class ProbeResult(
        val success: Boolean,
        val ttfbMs: Long = 0L,
        val handshakeMs: Long = 0L,
        val colo: String? = null,
        val isWsOk: Boolean = false,
        val errorMessage: String? = null
    )

    private val cfRayPattern = Pattern.compile("(?i)^cf-ray:\\s*[a-f0-9]+-([a-z]{3})")

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

    suspend fun probeEndpoint(
        ip: String,
        port: Int,
        host: String = KhorshidConstants.DEFAULT_CF_HOST,
        timeoutMs: Int = 3000,
        requireWebSocket: Boolean = false
    ): ProbeResult = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null

        try {
            val tcpStart = System.currentTimeMillis()
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(ip, port), timeoutMs)
            val tcpLatency = System.currentTimeMillis() - tcpStart

            sslSocket = (sslContext.socketFactory.createSocket(rawSocket, ip, port, true) as SSLSocket).apply {
                soTimeout = timeoutMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && host.isNotBlank()) {
                    try {
                        val params = sslParameters
                        params.serverNames = listOf(SNIHostName(host))
                        sslParameters = params
                    } catch (_: Exception) {}
                }
                enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2").filter { it in supportedProtocols }.toTypedArray()
            }

            val tlsStart = System.currentTimeMillis()
            sslSocket.startHandshake()
            val handshakeMs = System.currentTimeMillis() - tlsStart

            val requestStart = System.currentTimeMillis()
            val writer = OutputStreamWriter(sslSocket.outputStream, "UTF-8")

            val req = if (requireWebSocket) {
                "GET / HTTP/1.1\r\n" +
                        "Host: $host\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n" +
                        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n\r\n"
            } else {
                "GET ${KhorshidConstants.TRACE_PATH} HTTP/1.1\r\n" +
                        "Host: $host\r\n" +
                        "User-Agent: KhorshidScanner/1.0\r\n" +
                        "Connection: close\r\n\r\n"
            }

            writer.write(req)
            writer.flush()

            val reader = BufferedReader(InputStreamReader(sslSocket.inputStream, "UTF-8"))
            val firstLine = reader.readLine() ?: return@withContext ProbeResult(success = false, errorMessage = "Empty response")
            val ttfb = System.currentTimeMillis() - requestStart

            var isCloudflare = false
            var isWsValid = false
            var colo: String? = null

            // بررسی خط اول وضعیت HTTP (مثلاً HTTP/1.1 101 یا 400 یا 200)
            val statusCode = firstLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0

            if (requireWebSocket) {
                // کدهای معتبر در پاسخ وب‌سوکت که نشان می‌دهد ارتباط از DPI عبور کرده است
                // ۱۰۱: تایید وب‌سوکت | ۴۰۰ یا ۴۲۶ یا ۴۰۴: پاسخ مستقیم سرور کلودفلر به درخواست وب‌سوکت
                isWsValid = (statusCode == 101 || statusCode == 400 || statusCode == 426 || statusCode == 404 || statusCode in 200..299)
            }

            // خواندن هدرها برای پیدا کردن دیتاسنتر (CF-RAY) و اطمینان از کلودفلر بودن سرور
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break // پایان هدرها -> نیازی به خواندن بادی نیست!

                val lowerLine = line!!.lowercase()
                if (lowerLine.startsWith("server:") && lowerLine.contains("cloudflare")) {
                    isCloudflare = true
                }

                // استخراج نام دیتاسنتر از هدر CF-RAY (مثال: CF-RAY: 8234ab12cd-FRA -> FRA)
                val matcher = cfRayPattern.matcher(line!!)
                if (matcher.find()) {
                    colo = matcher.group(1)?.uppercase()
                    isCloudflare = true
                }

                // اگر در حالت Trace عادی بودیم و در بادی colo= آمد
                if (!requireWebSocket && line!!.startsWith("colo=")) {
                    colo = line!!.substringAfter("colo=").trim().uppercase()
                }
            }

            // اگر درخواست Trace بود، بخش کوچکی از بادی را هم برای colo= می‌خوانیم
            if (!requireWebSocket && colo == null) {
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("colo=")) {
                        colo = line!!.substringAfter("colo=").trim().uppercase()
                        break
                    }
                }
            }

            val finalSuccess = if (requireWebSocket) {
                isWsValid && (statusCode != 0)
            } else {
                statusCode in 200..399 || isCloudflare
            }

            ProbeResult(
                success = finalSuccess,
                ttfbMs = tcpLatency + handshakeMs + ttfb,
                handshakeMs = handshakeMs,
                colo = colo ?: if (isCloudflare) "CF" else "UNKNOWN",
                isWsOk = isWsValid
            )
        } catch (e: Exception) {
            ProbeResult(success = false, errorMessage = e.message)
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }
}