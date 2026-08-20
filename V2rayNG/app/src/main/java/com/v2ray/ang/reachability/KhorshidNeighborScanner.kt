package com.v2ray.ang.reachability

object KhorshidNeighborScanner {
    fun getSubnet24Neighbors(targetIp: String): List<String> {
        val parts = targetIp.split(".")
        if (parts.size != 4) return emptyList()
        val base = "${parts[0]}.${parts[1]}.${parts[2]}"
        return (1..254).map { "$base.$it" }
    }
}
