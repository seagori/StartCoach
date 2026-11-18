package com.seagori.hgr.domain

import com.seagori.hgr.i18n.AppLocale
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 예외 메시지에 포함된 호스트/내부 정보를 UI에 그대로 노출하지 않는다. */
fun Throwable.toUserMessage(fallback: String): String = when (this) {
    is UnknownHostException -> AppLocale.strings.serverUnreachable
    is SocketTimeoutException -> AppLocale.strings.serverTimeout
    is SSLException -> AppLocale.strings.sslFailed
    else -> message?.takeIf {
        it.isNotBlank() &&
            it.length <= 120 &&
            !it.contains("://") &&
            !it.contains("Failed to connect", ignoreCase = true) &&
            !it.contains("Unable to resolve host", ignoreCase = true)
    } ?: fallback
}
