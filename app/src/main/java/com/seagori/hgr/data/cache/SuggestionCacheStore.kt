package com.seagori.hgr.data.cache

import android.content.Context
import com.seagori.hgr.domain.Choice
import com.seagori.hgr.domain.SuggestionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
private data class CachedChoiceDto(
    val text: String,
    val difficulty: Int
)

/**
 * 목표별 첫 제안 사전 캐시 저장소 (Zero-Wait Start).
 * 목표를 여는 즉시 대기 시간 없이 가장 가벼운 행동 2~3개를 표시한다.
 */
class SuggestionCacheStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("startcoach_suggestion_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun get(goalId: Long): List<Choice> {
        val raw = prefs.getString(key(goalId), "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CachedChoiceDto.serializer()), raw)
                .map { Choice(text = it.text, difficulty = it.difficulty, kind = SuggestionKind.ACTION) }
        }.getOrDefault(emptyList())
    }

    fun easiestAction(goalId: Long): String? =
        get(goalId).minByOrNull { it.difficulty }?.text?.takeIf { it.isNotBlank() }

    fun save(goalId: Long, choices: List<Choice>) {
        val normalized = choices
            .filter { it.text.isNotBlank() }
            .sortedBy { it.difficulty }
            .take(3)
        if (normalized.isEmpty()) return
        val encoded = json.encodeToString(
            ListSerializer(CachedChoiceDto.serializer()),
            normalized.map { CachedChoiceDto(it.text, it.difficulty) }
        )
        prefs.edit().putString(key(goalId), encoded).apply()
        _version.value += 1
    }

    fun remove(goalId: Long) {
        if (prefs.contains(key(goalId))) {
            prefs.edit().remove(key(goalId)).apply()
            _version.value += 1
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _version.value += 1
    }

    private fun key(goalId: Long): String = "goal_$goalId"
}
