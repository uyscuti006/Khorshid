package com.v2ray.ang.reachability

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

object VpnStateGuard {

    /**
     * بررسی اینکه آیا VPN متفرقه‌ای (غیر از این برنامه) فعال است یا خیر.
     */
    fun isForeignVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * ثبت لیسنر برای تشخیص اتصال ناگهانی VPN در حین اسکن
     */
    fun registerVpnAppearedCallback(
        context: Context,
        onVpnAppeared: () -> Unit
    ): ConnectivityManager.NetworkCallback {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onVpnAppeared()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    onVpnAppeared()
                }
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {}
        return callback
    }

    fun unregister(context: Context, callback: ConnectivityManager.NetworkCallback) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
    }
}