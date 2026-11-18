package com.seagori.hgr.data.io

import com.seagori.hgr.i18n.AppLocale
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** 외부 입력(백업/문서/LLM 응답)의 최대 크기. 압축 폭탄과 OOM을 방지한다. */
internal object SizeLimits {
    const val BACKUP_BYTES = 20 * 1024 * 1024
    const val MARKDOWN_BYTES = 5 * 1024 * 1024
    const val LLM_RESPONSE_BYTES = 1024 * 1024
    const val BACKUP_ENTRIES = 128
    /** data.json 이외 ZIP 항목을 버릴 때 항목당 최대 읽기량(압축 폭탄 완화) */
    const val BACKUP_DRAIN_BYTES = 64 * 1024
}

/** [maxBytes]를 넘는 순간 예외를 던지며 스트림을 읽는다. */
internal fun InputStream.readLimited(maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) {
            throw IOException(AppLocale.strings.inputTooLarge(maxBytes / (1024 * 1024)))
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
