package com.seagori.hgr.data.curriculum

import com.seagori.hgr.data.local.dao.CurriculumProgressDao
import com.seagori.hgr.data.local.dao.MaterialChunkDao
import com.seagori.hgr.data.local.dao.MaterialDao
import com.seagori.hgr.data.local.entity.CurriculumProgressEntity
import com.seagori.hgr.data.local.entity.MaterialChunkEntity
import com.seagori.hgr.data.local.entity.MaterialEntity
import com.seagori.hgr.domain.CurriculumExcerpt
import com.seagori.hgr.domain.MarkdownChunker
import com.seagori.hgr.domain.Outcome
import com.seagori.hgr.i18n.AppLocale
import androidx.room.withTransaction
import java.security.MessageDigest

class CurriculumMemoryService(
    private val db: com.seagori.hgr.data.local.AppDatabase,
    private val materialDao: MaterialDao,
    private val chunkDao: MaterialChunkDao,
    private val progressDao: CurriculumProgressDao
) {
    suspend fun importMarkdown(goalId: Long, title: String, markdown: String): Long {
        val hash = sha256(markdown)
        val now = System.currentTimeMillis()
        val chunks = MarkdownChunker.chunk(markdown, title)
        var createdId = 0L
        db.withTransaction {
            val materialId = materialDao.insert(
                MaterialEntity(
                    goalId = goalId,
                    title = title.ifBlank { AppLocale.strings.materialFallback },
                    markdown = markdown,
                    markdownHash = hash,
                    importedAt = now
                )
            )
            createdId = materialId
            chunkDao.insertAll(
                chunks.map {
                    MaterialChunkEntity(
                        materialId = materialId,
                        headingPath = it.headingPath,
                        body = it.body,
                        sortOrder = it.order
                    )
                }
            )
            progressDao.upsert(
                CurriculumProgressEntity(
                    goalId = goalId,
                    materialId = materialId,
                    chunkIndex = 0,
                    updatedAt = now
                )
            )
        }
        return createdId
    }

    suspend fun materialsForGoal(goalId: Long) = materialDao.listByGoal(goalId)

    data class MaterialOutline(
        val materialId: Long,
        val title: String,
        val text: String
    )

    /** 경로 설계용으로 최신 자료의 제목/목차/본문 일부를 압축해 넘긴다. */
    suspend fun outlineForGoal(goalId: Long, maxChars: Int = 6_000): MaterialOutline? {
        val material = materialDao.listByGoal(goalId).firstOrNull() ?: return null
        val chunks = chunkDao.listForMaterial(material.id)
        if (chunks.isEmpty()) return null
        val text = buildString {
            append("# ").append(material.title).append('\n')
            for (chunk in chunks) {
                if (length >= maxChars) break
                append("\n## ").append(chunk.headingPath).append('\n')
                append(chunk.body.take(500)).append('\n')
            }
        }.take(maxChars)
        return MaterialOutline(material.id, material.title, text)
    }


    suspend fun deleteMaterial(materialId: Long) {
        materialDao.deleteById(materialId)
    }

    suspend fun progressLine(goalId: Long): String? {
        val progress = progressDao.getByGoal(goalId) ?: return null
        val material = materialDao.getById(progress.materialId) ?: return null
        val chunks = chunkDao.listForMaterial(material.id)
        if (chunks.isEmpty()) return AppLocale.strings.progressBeforeStart(material.title)
        val idx = progress.chunkIndex.coerceIn(0, chunks.lastIndex)
        val chunk = chunks[idx]
        return "${material.title} · ${chunk.headingPath} (${idx + 1}/${chunks.size})"
    }

    suspend fun buildExcerpt(goalId: Long): CurriculumExcerpt? {
        val progress = progressDao.getByGoal(goalId) ?: return null
        val chunks = chunkDao.listForMaterial(progress.materialId)
        if (chunks.isEmpty()) return null
        val idx = progress.chunkIndex.coerceIn(0, chunks.lastIndex)
        val current = chunks[idx]
        val next = chunks.getOrNull(idx + 1)
        val text = buildString {
            append(AppLocale.strings.promptCurrent).append(current.headingPath).append('\n').append(current.body.take(600))
            next?.let {
                append(AppLocale.strings.promptNext).append(it.headingPath).append('\n').append(it.body.take(250))
            }
        }.take(900)
        return CurriculumExcerpt(label = current.headingPath, text = text)
    }

    /** 경로 단계가 참조한 자료 heading의 본문을 세션에 보여줄 만큼만 가져온다. */
    suspend fun excerptForHeading(goalId: Long, heading: String?, maxChars: Int = 480): String? {
        val clean = heading?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val material = materialDao.listByGoal(goalId).firstOrNull() ?: return null
        val chunks = chunkDao.listForMaterial(material.id)
        if (chunks.isEmpty()) return null
        val exact = chunks.firstOrNull { it.headingPath.equals(clean, ignoreCase = true) }
        val partial = exact ?: chunks.firstOrNull {
            it.headingPath.contains(clean, ignoreCase = true) ||
                clean.contains(it.headingPath, ignoreCase = true)
        }
        val chunk = partial ?: return null
        return chunk.body.trim().take(maxChars).ifBlank { null }
    }

    suspend fun advanceAfterOutcome(goalId: Long, outcome: Outcome) {
        if (outcome != Outcome.DONE) return
        val progress = progressDao.getByGoal(goalId) ?: return
        val chunks = chunkDao.listForMaterial(progress.materialId)
        if (chunks.isEmpty()) return
        val nextIndex = (progress.chunkIndex + 1).coerceAtMost(chunks.lastIndex)
        if (nextIndex == progress.chunkIndex) return
        progressDao.upsert(
            progress.copy(chunkIndex = nextIndex, updatedAt = System.currentTimeMillis())
        )
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
