package com.seagori.hgr.domain

data class MarkdownChunk(
    val headingPath: String,
    val body: String,
    val order: Int
)

object MarkdownChunker {
    private val headingPattern = Regex("^(#{1,3})\\s+(.+)$", RegexOption.MULTILINE)

    fun chunk(markdown: String, title: String): List<MarkdownChunk> {
        val normalized = markdown.trim()
        if (normalized.isEmpty()) return emptyList()
        val lines = normalized.lines()
        val chunks = mutableListOf<MarkdownChunk>()
        var currentHeading = title
        val buffer = StringBuilder()
        var order = 0

        fun flush() {
            val body = buffer.toString().trim()
            if (body.isNotEmpty()) {
                chunks += MarkdownChunk(currentHeading, body.take(4_000), order++)
            }
            buffer.clear()
        }

        for (line in lines) {
            val match = headingPattern.matchEntire(line.trim())
            if (match != null) {
                flush()
                currentHeading = match.groupValues[2].trim()
            } else {
                buffer.appendLine(line)
            }
        }
        flush()
        if (chunks.isEmpty()) {
            chunks += MarkdownChunk(title, normalized.take(4_000), 0)
        }
        return chunks
    }
}
