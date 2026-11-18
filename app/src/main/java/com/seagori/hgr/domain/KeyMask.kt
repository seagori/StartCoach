package com.seagori.hgr.domain

/** API 키를 앞 3자와 뒤 2자만 남기고 가린다. 예: sk-...-DS */
fun maskApiKey(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    if (value.length <= 5) return value
    val prefix = value.take(3)
    val suffix = value.takeLast(2)
    return if (prefix.equals("sk-", ignoreCase = true)) {
        "$prefix...-$suffix"
    } else {
        "$prefix...$suffix"
    }
}
