package com.v2ray.ang.reachability

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

object KhorshidIspDetector {

    enum class Operator(val displayName: String, val ranges: List<String>) {
        ALL("All", KhorshidConstants.MCI_RANGES + KhorshidConstants.IRANCELL_RANGES + KhorshidConstants.RIGHTEL_RANGES + KhorshidConstants.FIXED_ISP_RANGES + KhorshidConstants.GENERAL_CF_RANGES),
        MCI("MCI", KhorshidConstants.MCI_RANGES + KhorshidConstants.GENERAL_CF_RANGES),
        IRANCELL("Irancell", KhorshidConstants.IRANCELL_RANGES + KhorshidConstants.GENERAL_CF_RANGES),
        RIGHTEL("Rightel", KhorshidConstants.RIGHTEL_RANGES + KhorshidConstants.GENERAL_CF_RANGES),
        WIFI_FIXED("Wi-Fi / Fixed", KhorshidConstants.FIXED_ISP_RANGES + KhorshidConstants.GENERAL_CF_RANGES),
        GENERAL("General", KhorshidConstants.GENERAL_CF_RANGES)
    }

    fun detect(context: Context): Operator {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return Operator.GENERAL
            val caps = cm.getNetworkCapabilities(net) ?: return Operator.GENERAL

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return Operator.WIFI_FIXED
            }

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val opCode = tm.simOperator ?: tm.networkOperator ?: ""
                val opName = (tm.simOperatorName + " " + tm.networkOperatorName).lowercase()

                return when {
                    opCode.startsWith("43214") || opName.contains("mci") || opName.contains("hamrah") || opName.contains("tci") -> Operator.MCI
                    opCode.startsWith("43235") || opName.contains("irancell") || opName.contains("mtn") -> Operator.IRANCELL
                    opCode.startsWith("43220") || opName.contains("rightel") -> Operator.RIGHTEL
                    else -> Operator.GENERAL
                }
            }
        } catch (_: Exception) {}
        return Operator.GENERAL
    }
}
