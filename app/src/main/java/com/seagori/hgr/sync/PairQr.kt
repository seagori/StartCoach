package com.seagori.hgr.sync

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * QR 페어링 payload.
 * 데스크탑(서버)이 표시하고 Android(클라이언트)가 스캔한다.
 *
 * startcoach://pair?v=2&host=192.168.0.10&port=45871&fp=<sha256>&code=123456&name=PC
 */
data class PairQrPayload(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val code: String,
    val name: String = ""
)

object PairQr {
    const val SCHEME = "startcoach"
    const val VERSION = 2
    private const val PATH = "pair"

    fun encode(host: String, port: Int, fingerprint: String, code: String, name: String = ""): String {
        val builder = StringBuilder("$SCHEME://$PATH?v=$VERSION")
        builder.append("&host=").append(urlEncode(host.trim()))
        builder.append("&port=").append(port)
        builder.append("&fp=").append(urlEncode(SyncCrypto.normalizeFingerprint(fingerprint)))
        builder.append("&code=").append(urlEncode(code.trim()))
        if (name.isNotBlank()) builder.append("&name=").append(urlEncode(name.trim()))
        return builder.toString()
    }

    /** 잘못된 QR이나 다른 QR을 스캔했을 때 null을 돌려준다. */
    fun parse(text: String): PairQrPayload? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val prefix = "$SCHEME://"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
        val rest = trimmed.substring(prefix.length)
        val queryIndex = rest.indexOf('?')
        if (queryIndex <= 0) return null
        val path = rest.substring(0, queryIndex).trim('/')
        if (!path.equals(PATH, ignoreCase = true)) return null
        val params = parseQuery(rest.substring(queryIndex + 1))
        val host = params["host"].orEmpty().trim()
        if (host.isBlank()) return null
        val fingerprint = SyncCrypto.normalizeFingerprint(params["fp"])
        if (fingerprint.isBlank()) return null
        val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: SyncProtocol.DEFAULT_PORT
        val code = params["code"].orEmpty().filter { it.isDigit() }
        if (code.length !in 4..8) return null
        return PairQrPayload(
            host = host,
            port = port,
            fingerprint = fingerprint,
            code = code,
            name = params["name"].orEmpty()
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        query.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val index = part.indexOf('=')
            if (index <= 0) return@forEach
            val key = part.substring(0, index).lowercase()
            val value = runCatching { urlDecode(part.substring(index + 1)) }.getOrDefault("")
            result[key] = value
        }
        return result
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")
}
