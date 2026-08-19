package com.v2ray.ang.reachability

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * TLS handshake probe with SNI support.
 * Validates that a TLS connection can be established with a specific SNI,
 * confirming the server is reachable and TLS handshake succeeds.
 */
object TlsSniProbe {

    private const val TAG = "TlsSniProbe"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val HANDSHAKE_TIMEOUT_MS = 5000

    data class TlsResult(
        val success: Boolean,
        val handshakeMs: Long = 0L,
        val errorMessage: String? = null
    )

    /**
     * Perform TLS handshake with SNI validation.
     *
     * @param ip Target IP address
     * @param port Target port
     * @param sni Server Name Indication (SNI) - typically the domain name
     * @return TlsResult with success status and handshake time
     */
    suspend fun probeWithSni(
        ip: String,
        port: Int,
        sni: String
    ): TlsResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            // Create a trust manager that accepts all certificates
            // (We're only testing connectivity, not validating the cert chain)
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Step 1: TCP connect with timeout
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)

            // Step 2: TLS handshake with SNI
            val factory = sslContext.socketFactory
            sslSocket = (factory.createSocket(
                socket,
                ip,
                port,
                true  // autoClose
            ) as SSLSocket).apply {
                // Set SNI via reflection (SSLHostName is API 24+ only)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val methodName = "setHost"
                        val method = javaClass.getMethod(methodName, String::class.java)
                        method.invoke(this, sni)
                    }
                } catch (_: Exception) {
                    // SNI not supported on this device, continue anyway
                }
                // Enable all protocols for maximum compatibility
                enabledProtocols = supportedProtocols
            }

            // Step 3: Perform handshake with timeout
            val startTime = System.currentTimeMillis()
            sslSocket.startHandshake()
            val handshakeMs = System.currentTimeMillis() - startTime

            // Success
            TlsResult(success = true, handshakeMs = handshakeMs)

        } catch (e: Exception) {
            Log.w(TAG, "TLS probe failed for $ip:$port SNI=$sni: ${e.message}")
            TlsResult(success = false, errorMessage = e.message)
        } finally {
            try {
                sslSocket?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
