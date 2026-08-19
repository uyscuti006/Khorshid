package com.v2ray.ang.reachability

import kotlin.random.Random

object CloudflareIpPool {

    private val cfRanges = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22"
    )

    // ساخت پیش‌فرض بازه‌های عددی CIDR برای افزایش سرعت
    private val parsedRanges: List<LongRange> by lazy {
        cfRanges.mapNotNull { parseCidrToRange(it) }
    }

    private val ipRegex = Regex("""^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.){3}(25[0-5]|(2[0-4]|1\d|[1-9]|)\d)$""")

    fun generateRandomIps(count: Int): List<String> {
        if (parsedRanges.isEmpty() || count <= 0) return emptyList()

        val result = mutableSetOf<String>()
        var attempts = 0
        val maxAttempts = count * 5

        while (result.size < count && attempts < maxAttempts) {
            attempts++
            val randomRange = parsedRanges[Random.nextInt(parsedRanges.size)]
            val randomIpLong = Random.nextLong(randomRange.first, randomRange.last + 1)
            result.add(longToIp(randomIpLong))
        }

        return result.toList()
    }

    fun parseCustomIps(text: String): List<String> {
        return text.lines()
            .map { it.trim() }
            .filter { ipRegex.matches(it) }
            .distinct()
    }

    // تبدیل CIDR به بازه Long (مثلاً 192.168.1.0/24)
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
            (parts[0].toLong() shl 24) or
                    (parts[1].toLong() shl 16) or
                    (parts[2].toLong() shl 8) or
                    parts[3].toLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun longToIp(ip: Long): String {
        return "${(ip shrink 24) and 0xFF}.${(ip shrink 16) and 0xFF}.${(ip shrink 8) and 0xFF}.${ip and 0xFF}"
    }

    private infix fun Long.shrink(shift: Int): Long = this ushr shift
}