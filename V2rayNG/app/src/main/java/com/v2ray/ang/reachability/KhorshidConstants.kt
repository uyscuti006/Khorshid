package com.v2ray.ang.reachability

import kotlin.random.Random

object KhorshidConstants {
    val CLOUDFLARE_PORTS = listOf(443, 8443, 2053, 2083, 2087, 2096)

    const val TRACE_PATH = "/cdn-cgi/trace"
    const val SPEED_TEST_PATH = "/__down?bytes=524288"
    const val DEFAULT_CF_HOST = "speed.cloudflare.com"

    val GENERAL_CF_RANGES = listOf(
        "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
        "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
        "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
        "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22"
    )

    val MCI_RANGES = listOf("104.18.0.0/16", "104.22.0.0/16", "172.67.0.0/16", "188.114.96.0/22", "162.159.0.0/16")
    val IRANCELL_RANGES = listOf("104.26.0.0/16", "104.27.0.0/16", "172.64.0.0/16", "104.19.0.0/16", "108.162.192.0/20")
    val RIGHTEL_RANGES = listOf("104.21.0.0/16", "172.67.0.0/16", "141.101.64.0/20", "198.41.128.0/20")
    val FIXED_ISP_RANGES = listOf("104.16.0.0/14", "104.20.0.0/14", "172.64.0.0/14", "162.158.0.0/16")

    fun generateRandomIps(count: Int, ranges: List<String>): List<String> {
        val parsedRanges = ranges.mapNotNull { parseCidrToRange(it) }
        if (parsedRanges.isEmpty() || count <= 0) return emptyList()

        val result = mutableSetOf<String>()
        var attempts = 0
        val maxAttempts = count * 6

        while (result.size < count && attempts < maxAttempts) {
            attempts++
            val range = parsedRanges[Random.nextInt(parsedRanges.size)]
            val randomIpLong = Random.nextLong(range.first, range.last + 1)
            result.add(longToIp(randomIpLong))
        }

        return result.toList()
    }

    private fun parseCidrToRange(cidr: String): LongRange? {
        val parts = cidr.split("/")
        if (parts.size != 2) return null
        val ipLong = ipToLong(parts[0]) ?: return null
        val prefix = parts[1].toIntOrNull() ?: return null
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val startIp = ipLong and mask
        val endIp = startIp or mask.inv() and 0xFFFFFFFFL
        return startIp..endIp
    }

    private fun ipToLong(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return try {
            (parts[0].toLong() shl 24) or (parts[1].toLong() shl 16) or (parts[2].toLong() shl 8) or parts[3].toLong()
        } catch (_: Exception) { null }
    }

    private fun longToIp(ip: Long): String {
        return "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"
    }
}
