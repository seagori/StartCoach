package com.seagori.hgr.data.remote

import com.seagori.hgr.data.settings.SecureTokenStore
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.GoalDomain
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.PathPlan
import com.seagori.hgr.domain.PathPlanRequest
import com.seagori.hgr.domain.SuggestionContext
import com.seagori.hgr.domain.SuggestionResult
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.i18n.AppLocale
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 빠른 제안과 경로/세션 코치를 서로 다른 엔드포인트로 호출한다.
 * 코치 주소가 비어 있으면 빠른 제안 설정으로 대체해 단일 모델 구성에서도 동작한다.
 */
class LlmSuggestionGateway(
    private val settings: SettingsStore,
    private val tokens: SecureTokenStore
) {
    private val http = LlmHttp(sharedClient)
    private val provider = OpenAiCompatProvider(LlmHttp.json())

    fun isRemoteEnabled(): Boolean = resolve(LlmProfile.FAST) != null

    fun isCoachConfigured(): Boolean = resolve(LlmProfile.COACH) != null

    suspend fun fetchChoices(context: SuggestionContext): Result<SuggestionResult> {
        val endpoint = resolve(LlmProfile.FAST)
            ?: return Result.failure(IllegalStateException(AppLocale.strings.remoteNotConfigured))
        return provider.fetchChoices(
            http = http,
            context = context,
            baseUrl = endpoint.baseUrl,
            bearerToken = endpoint.token,
            modelId = endpoint.modelId,
            reasoningEffort = endpoint.reasoningEffort,
            coach = false
        )
    }

    suspend fun fetchCoachChoices(context: SuggestionContext): Result<SuggestionResult> {
        val endpoint = resolve(LlmProfile.COACH)
            ?: return Result.failure(IllegalStateException(AppLocale.strings.remoteNotConfigured))
        return provider.fetchChoices(
            http = http,
            context = context,
            baseUrl = endpoint.baseUrl,
            bearerToken = endpoint.token,
            modelId = endpoint.modelId,
            reasoningEffort = endpoint.reasoningEffort,
            coach = true
        )
    }

    suspend fun designPath(request: PathPlanRequest): Result<PathPlan> {
        val endpoint = resolve(LlmProfile.COACH)
            ?: return Result.failure(IllegalStateException(AppLocale.strings.coachNotConfigured))
        return provider.designPath(
            http = http,
            request = request,
            baseUrl = endpoint.baseUrl,
            bearerToken = endpoint.token,
            modelId = endpoint.modelId,
            reasoningEffort = endpoint.reasoningEffort
        )
    }

    suspend fun testConnection(profile: LlmProfile): Result<String> {
        val endpoint = resolve(profile)
            ?: return Result.failure(IllegalStateException(AppLocale.strings.remoteDisabled))
        val sample = SuggestionContext(
            title = AppLocale.strings.testGoalTitle,
            domain = GoalDomain.OTHER,
            lowered = false,
            stepTitle = if (profile == LlmProfile.COACH) AppLocale.strings.testStepTitle else null,
            stepInstruction = if (profile == LlmProfile.COACH) AppLocale.strings.testStepInstruction else null,
            stepCriteria = if (profile == LlmProfile.COACH) listOf(AppLocale.strings.testStepCriterion) else emptyList(),
            stepPosition = if (profile == LlmProfile.COACH) "1/1" else null
        )
        val started = System.nanoTime()
        val result = provider.fetchChoices(
            http = http,
            context = sample,
            baseUrl = endpoint.baseUrl,
            bearerToken = endpoint.token,
            modelId = endpoint.modelId,
            reasoningEffort = endpoint.reasoningEffort,
            coach = profile == LlmProfile.COACH
        )
        return result.map { value ->
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            val message = when (value) {
                is SuggestionResult.Choices -> AppLocale.strings.connectionSuccess(value.choices.size)
                is SuggestionResult.Clarify -> AppLocale.strings.connectionClarify
            }
            AppLocale.strings.connectionWithDuration(message, elapsedMs)
        }
    }

    private data class Endpoint(
        val baseUrl: String,
        val modelId: String,
        val token: String?,
        val reasoningEffort: String?
    )

    private fun resolve(profile: LlmProfile): Endpoint? {
        val directUrl = settings.apiBaseUrl(profile)
        val fallbackUrl = settings.apiBaseUrl(LlmProfile.FAST)
        val (rawUrl, actualProfile) = when {
            directUrl.isNotBlank() -> directUrl to profile
            profile == LlmProfile.COACH && fallbackUrl.isNotBlank() -> fallbackUrl to LlmProfile.FAST
            else -> return null
        }
        val baseUrl = UrlRules.normalize(rawUrl) ?: return null
        val origin = UrlRules.origin(baseUrl) ?: return null
        val modelId = settings.modelId(actualProfile).ifBlank { defaultModel(actualProfile) }
        val effort = if (actualProfile == LlmProfile.COACH) {
            settings.coachReasoningEffort.ifBlank { SettingsStore.DEFAULT_REASONING }
        } else {
            null
        }
        return Endpoint(
            baseUrl = baseUrl,
            modelId = modelId,
            token = tokens.tokenFor(actualProfile, origin),
            reasoningEffort = effort
        )
    }

    private fun defaultModel(profile: LlmProfile): String = when (profile) {
        LlmProfile.FAST -> "google.gemma-4-26b-a4b"
        LlmProfile.COACH -> "google.gemma-4-26b-a4b"
    }

    private companion object {
        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .dns(SafeDns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
