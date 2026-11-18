package com.seagori.hgr.data.remote

import com.seagori.hgr.data.io.SizeLimits
import com.seagori.hgr.data.io.readLimited
import com.seagori.hgr.domain.Choice
import com.seagori.hgr.domain.SuggestionKind
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.i18n.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.UnknownHostException

/** 연결 직전 해석된 모든 IP를 검사해 DNS rebinding 및 사설망 SSRF를 차단한다. */
internal object SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (UrlRules.isBlockedHostname(hostname)) {
            throw UnknownHostException("Blocked host")
        }
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any { UrlRules.isBlockedAddress(it) }) {
            throw UnknownHostException("Blocked private or local address")
        }
        return addresses
    }
}

/** LLM providers가 공유하는 JSON/HTTP 실행기 */
internal class LlmHttp(private val client: OkHttpClient) {
    suspend fun post(url: String, payload: String, bearerToken: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(JSON))
                if (!bearerToken.isNullOrBlank()) builder.header("Authorization", "Bearer $bearerToken")
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) error(httpErrorMessage(response.code))
                    val body = response.body ?: return@use ""
                    if (body.contentLength() > SizeLimits.LLM_RESPONSE_BYTES) error(AppLocale.strings.responseTooLarge)
                    body.byteStream().use { it.readLimited(SizeLimits.LLM_RESPONSE_BYTES) }.decodeToString()
                }
            }
        }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401 -> AppLocale.strings.errorUnauthorized
        403 -> AppLocale.strings.errorForbidden
        404 -> AppLocale.strings.errorNotFound
        429 -> AppLocale.strings.errorRateLimited
        else -> AppLocale.strings.serverResponse(code)
    }

    internal companion object {
        internal fun json(): Json = Json { ignoreUnknownKeys = true }

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
internal data class ChoiceDto(val text: String, val difficulty: Int, val kind: String)

private const val MAX_CHOICES = 4
private const val MAX_CHOICE_TEXT = 200

internal fun List<ChoiceDto>.toChoices(): List<Choice> = asSequence()
    .filter { it.text.isNotBlank() }
    .take(MAX_CHOICES)
    .map {
        Choice(
            text = it.text.trim().take(MAX_CHOICE_TEXT),
            difficulty = it.difficulty.coerceIn(0, 3),
            kind = SuggestionKind.ACTION
        )
    }
    .toList()
