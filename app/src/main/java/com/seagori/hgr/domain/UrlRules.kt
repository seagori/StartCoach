package com.seagori.hgr.domain

import java.net.InetAddress
import java.net.URI

/** 사용자가 입력한 LLM API 주소를 검증/정규화한다. */
object UrlRules {
    private const val MAX_LENGTH = 2_048

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isBlank()) return null
        if (isPrivateOrLocalHost(host)) return null
        if (uri.userInfo != null) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return trimmed.trimEnd('/')
    }

    /** 토큰을 묶을 origin. 같은 호스트의 다른 포트로는 전송되지 않는다. */
    fun origin(raw: String): String? {
        val normalized = normalize(raw) ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1) 443 else uri.port
        return "${uri.scheme.lowercase()}://$host:$port"
    }

    fun isBlockedHostname(host: String): Boolean {
        val clean = host.trim().trimEnd('.').lowercase()
        return clean == "localhost" ||
            clean.endsWith(".localhost") ||
            clean.endsWith(".local") ||
            clean.endsWith(".internal")
    }

    fun isBlockedAddress(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress
        ) {
            return true
        }
        val bytes = addr.address
        // IPv4 CGNAT (100.64.0.0/10)
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 100 && (b1 and 0xC0) == 64) return true
        }
        // IPv6 Unique Local Address (fc00::/7)
        if (bytes.size == 16) {
            val b0 = bytes[0].toInt() and 0xFF
            if ((b0 and 0xFE) == 0xFC) return true
        }
        return false
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        if (isBlockedHostname(host)) return true
        val unbracketed = host.removePrefix("[").removeSuffix("]")
        if (!isIpLiteral(unbracketed)) return false
        val addr = runCatching { InetAddress.getByName(unbracketed) }.getOrNull() ?: return true
        return isBlockedAddress(addr)
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.contains(':')) {
            return host.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' || it == '%' }
        }
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull()
            n != null && n in 0..255
        }
    }
}
