package com.v2ray.ang.reachability

import com.v2ray.ang.dto.entities.ProfileItem

object KhorshidConfigAnalyzer {

    enum class ConfigType {
        CLOUDFLARE_CDN,
        DIRECT_INCOMPATIBLE
    }

    data class AnalysisResult(
        val type: ConfigType,
        val isCompatible: Boolean,
        val sni: String,
        val host: String,
        val reason: String
    )

    fun analyze(config: ProfileItem): AnalysisResult {
        val network = config.network.orEmpty().lowercase()
        val security = config.security.orEmpty().lowercase()
        val sni = config.sni.orEmpty().ifBlank { config.server.orEmpty() }
        val host = config.host.orEmpty().ifBlank { sni }

        if (security == "reality") {
            return AnalysisResult(
                type = ConfigType.DIRECT_INCOMPATIBLE,
                isCompatible = false,
                sni = sni,
                host = host,
                reason = "Reality config is direct and does not work with Cloudflare IPs."
            )
        }

        val isCdnTransport = network in listOf("ws", "grpc", "httpupgrade", "splithttp")
        val hasTls = security == "tls" || security.isBlank()

        if (isCdnTransport && hasTls) {
            return AnalysisResult(
                type = ConfigType.CLOUDFLARE_CDN,
                isCompatible = true,
                sni = sni,
                host = host,
                reason = "CDN/Worker config compatible with clean IPs."
            )
        }

        return AnalysisResult(
            type = ConfigType.DIRECT_INCOMPATIBLE,
            isCompatible = false,
            sni = sni,
            host = host,
            reason = "Direct connection or no CDN."
        )
    }
}
