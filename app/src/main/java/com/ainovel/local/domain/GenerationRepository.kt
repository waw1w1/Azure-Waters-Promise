package com.ainovel.local.domain

import androidx.room.withTransaction
import com.ainovel.local.data.local.GenerationDao
import com.ainovel.local.data.local.ModelDao
import com.ainovel.local.data.local.NovelDao
import com.ainovel.local.data.local.NovelDatabase
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.ModelSettingEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.ReasoningKind
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.ResponseStatus
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.model.UsageRecordEntity
import com.ainovel.local.data.model.AssistantSessionEntity
import com.ainovel.local.data.remote.DeepSeekAdapter
import com.ainovel.local.data.remote.AnthropicCompatibleAdapter
import com.ainovel.local.data.remote.GeminiAdapter
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.LogicalContextItem
import com.ainovel.local.data.remote.PreparedRequest
import com.ainovel.local.data.remote.ProviderAdapter
import com.ainovel.local.data.remote.ProviderException
import com.ainovel.local.data.remote.ProviderMessage
import com.ainovel.local.data.remote.ProviderModel
import com.ainovel.local.data.remote.ProviderCatalog
import com.ainovel.local.data.remote.VertexAiServiceAccountAuth
import com.ainovel.local.data.remote.StreamEvent
import com.ainovel.local.data.remote.StreamResult
import com.ainovel.local.data.security.SecretStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface GenerationProgress {
    data class Started(val groupId: String, val candidateId: String) : GenerationProgress
    data class Delta(val candidateId: String, val content: String, val reasoning: String?) : GenerationProgress
    data class Completed(val groupId: String, val candidateId: String, val complete: Boolean) : GenerationProgress
    data class Failed(val groupId: String?, val candidateId: String?, val message: String, val partialSaved: Boolean) : GenerationProgress
    data object AssistantStarted : GenerationProgress
    data class AssistantDelta(val content: String, val reasoning: String?) : GenerationProgress
    data object AssistantCompleted : GenerationProgress
}

data class ProviderValidationResult(
    val models: List<ProviderModel>,
    val selectedModelId: String,
)

class GenerationRepository(
    private val database: NovelDatabase,
    private val novelDao: NovelDao,
    private val generationDao: GenerationDao,
    private val modelDao: ModelDao,
    private val secretStore: SecretStore,
    private val contextAssembler: ContextAssembler,
    private val json: Json,
    private val adapters: Map<String, ProviderAdapter> = ProviderCatalog.definitions.associate { provider ->
        provider.id to when (provider.id) {
            "anthropic", "anthropic-compatible" -> AnthropicCompatibleAdapter(providerId = provider.id, providerDisplayName = provider.displayName)
            "gemini", "vertex" -> GeminiAdapter(providerId = provider.id, providerDisplayName = provider.displayName)
            else -> DeepSeekAdapter(providerId = provider.id, providerDisplayName = provider.displayName)
        }
    },
) {
    private val _progress = MutableSharedFlow<GenerationProgress>(extraBufferCapacity = 64)
    val progress: Flow<GenerationProgress> = _progress.asSharedFlow()
    private val _activeCandidateId = MutableStateFlow<String?>(null)
    val activeCandidateId = _activeCandidateId.asStateFlow()

    fun readSavedApiKey(providerId: String): String? {
        val reference = "provider:$providerId:api-key"
        return secretStore.get(reference)
    }

    suspend fun recoverInterruptedGenerationState() = database.withTransaction {
        generationDao.recoverInterruptedGroups()
        generationDao.recoverInterruptedRequests()
    }

    suspend fun saveAndValidateProvider(
        providerId: String,
        displayName: String,
        baseUrl: String,
        apiKey: String,
        preferredModelId: String? = null,
    ): ProviderValidationResult {
        val adapter = adapters[providerId] ?: error("暂不支持该厂商")
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        require(normalizedBaseUrl.startsWith("https://")) { "Base URL 必须使用 HTTPS" }
        val existing = novelDao.provider(providerId)
        val existingSecret = existing?.let { secretStore.get(it.secretReference) }
        val validationKey = apiKey.takeIf(String::isNotBlank) ?: existingSecret
            ?: error("请输入 API Key")
        val secretReference = existing?.secretReference ?: "provider:$providerId:api-key"
        val pendingProvider = ProviderConfigEntity(
            providerId = providerId,
            displayName = displayName,
            baseUrl = normalizedBaseUrl,
            secretReference = secretReference,
            validationState = "CHECKING_MODEL_LIST",
            lastValidatedAt = existing?.lastValidatedAt,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val models = try {
            adapter.listModels(normalizedBaseUrl, validationKey)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is ProviderException && (error.statusCode == 401 || error.statusCode == 403)) throw error
            if (apiKey.isNotBlank()) secretStore.put(secretReference, apiKey)
            novelDao.upsertProvider(
                pendingProvider.copy(
                    validationState = "MODEL_LIST_UNAVAILABLE",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            throw IllegalStateException(
                "模型列表暂不可用；连接信息已安全保存，可填写手动模型 ID 继续真实验证。${error.message?.let { "（$it）" }.orEmpty()}",
                error,
            )
        }
        if (models.isEmpty()) {
            if (apiKey.isNotBlank()) secretStore.put(secretReference, apiKey)
            novelDao.upsertProvider(
                pendingProvider.copy(
                    validationState = "MODEL_LIST_UNAVAILABLE",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            error("模型列表为空；连接信息已安全保存，请填写手动模型 ID 继续真实验证。")
        }
        val selected = preferredModelId?.takeIf { id -> models.any { it.id == id } }
            ?: models.first().id
        if (apiKey.isNotBlank()) secretStore.put(secretReference, apiKey)
        database.withTransaction {
            novelDao.upsertProvider(
                pendingProvider.copy(
                    validationState = "VERIFIED",
                    lastValidatedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            modelDao.insertModelsWithoutOverwriting(
                models.map { model ->
                    ModelSettingEntity(
                        providerId = providerId,
                        modelId = model.id,
                        capabilitiesJson = json.encodeToString(model),
                        validated = true,
                    )
                },
            )
        }
        return ProviderValidationResult(models, selected)
    }

    suspend fun refreshModels(providerId: String): List<ProviderModel> {
        val provider = requireProvider(providerId)
        val apiKey = requireApiKey(provider)
        val adapter = adapters[providerId] ?: error("暂不支持该厂商")
        val models = adapter.listModels(provider.baseUrl, apiKey)
        require(models.isNotEmpty()) { "厂商模型列表为空，已保留此前缓存" }
        modelDao.insertModelsWithoutOverwriting(
            models.map { model ->
                ModelSettingEntity(
                    providerId = providerId,
                    modelId = model.id,
                    capabilitiesJson = json.encodeToString(model),
                    validated = true,
                )
            },
        )
        return models
    }

    suspend fun validateManualModel(
        providerId: String,
        modelId: String,
    ): ModelSettingEntity {
        require(modelId.isNotBlank()) { "请输入模型 ID" }
        val provider = requireProvider(providerId)
        val apiKey = requireApiKey(provider)
        val adapter = adapters[providerId] ?: error("暂不支持该厂商")
        val prepared = adapter.prepare(
            taskType = TaskType.MODEL_VALIDATION,
            novelId = null,
            targetEntityId = null,
            modelId = modelId.trim(),
            logicalContext = listOf(
                LogicalContextItem("validation", "VALIDATION", transmittedContent = "请只回复：连接成功", order = 0),
            ),
            messages = listOf(ProviderMessage("user", "请只回复：连接成功")),
            settings = GenerationSettings(streamOutput = false, thinkingEnabled = false, temperature = 0.0, maxOutputTokens = 8),
        )
        var finished = false
        adapter.stream(provider.baseUrl, apiKey, prepared).collect { event ->
            if (event is StreamEvent.Finished) finished = event.result.content.isNotBlank()
        }
        check(finished) { "手动模型没有返回可用文本" }
        val setting = ModelSettingEntity(
            providerId = providerId,
            modelId = modelId.trim(),
            capabilitiesJson = json.encodeToString(manualModelCapabilities(providerId, modelId.trim())),
            manualModel = true,
            validated = true,
        )
        database.withTransaction {
            modelDao.upsertModel(setting)
            novelDao.upsertProvider(
                provider.copy(
                    validationState = "VERIFIED_MANUAL",
                    lastValidatedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        return setting
    }

    companion object {
        fun manualModelCapabilities(providerId: String, modelId: String): ProviderModel {
            val cleanModel = VertexAiServiceAccountAuth.normalizeVertexModelId(modelId)
            val lower = cleanModel.lowercase()
            val isImageModel = lower.contains("imagen") || lower.contains("image")
            val isReasoningGemini = !isImageModel && (
                lower.startsWith("gemini-2.5") ||
                lower.startsWith("gemini-3") ||
                lower.startsWith("gemini-4") ||
                Regex("""^gemini-(?:[3-9]|\d{2,})(?:[.-].*|$)""").matches(lower) ||
                lower.contains("thinking")
            )
            val reasoning = (!isImageModel && (lower.contains("reason") || lower.contains("thinking"))) ||
                (providerId == "deepseek" && (lower.startsWith("deepseek-v4") || lower == "deepseek-reasoner")) ||
                (providerId == "openai" && (lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") || lower.startsWith("gpt-5"))) ||
                isReasoningGemini
            val reasoningStrength = (providerId == "deepseek" && lower.startsWith("deepseek-v4")) || isReasoningGemini
            return ProviderModel(
                id = modelId,
                supportsReasoning = reasoning,
                supportsReasoningStrength = reasoningStrength,
            )
        }
    }

    suspend fun generateBody(
        novelId: String,
        targetChapterId: String,
        oneTimeCreativeRequirement: String,
        settings: GenerationSettings,
        inspectOnly: Boolean = false,
    ): PreparedRequest {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        val providerId = novel.bodyProviderId ?: error("请先配置正文模型")
        val modelId = novel.bodyModelId ?: error("请先配置正文模型")
        val adapter = adapters[providerId] ?: error("暂不支持该厂商")
        val prepared = contextAssembler.prepareBody(
            novelId = novelId,
            targetChapterId = targetChapterId,
            providerId = providerId,
            modelId = modelId,
            oneTimeCreativeRequirement = oneTimeCreativeRequirement,
            settings = settings,
            adapter = adapter,
        )
        if (!inspectOnly) executeNewGroup(prepared, oneTimeCreativeRequirement, null)
        return prepared
    }

    suspend fun sendPreparedBody(prepared: PreparedRequest, oneTimeCreativeRequirement: String) {
        require(prepared.taskType == TaskType.BODY)
        require(oneTimeCreativeRequirement.isNotBlank())
        executeNewGroup(prepared, oneTimeCreativeRequirement, null)
    }

    suspend fun generateSummary(
        novelId: String,
        selections: List<SummarySourceSelection>,
        density: com.ainovel.local.data.model.SummaryDensity,
        settings: GenerationSettings,
        inspectOnly: Boolean = false,
    ): PreparedRequest {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        val providerId = novel.summaryProviderId ?: novel.bodyProviderId ?: error("请先配置总结模型")
        val modelId = novel.summaryModelId ?: novel.bodyModelId ?: error("请先配置总结模型")
        val adapter = adapters[providerId] ?: error("暂不支持该厂商")
        val prepared = contextAssembler.prepareSummary(
            novelId,
            selections,
            density,
            providerId,
            modelId,
            settings,
            adapter,
        )
        if (!inspectOnly) executeNewGroup(prepared, null, json.encodeToString(selections))
        return prepared
    }

    suspend fun sendPreparedSummary(
        prepared: PreparedRequest,
        selections: List<SummarySourceSelection>,
    ) {
        require(prepared.taskType == TaskType.SUMMARY)
        executeNewGroup(prepared, null, json.encodeToString(selections))
    }

    suspend fun generateAssistantReply(
        novelId: String,
        message: String,
        settings: GenerationSettings,
        inspectOnly: Boolean = false,
    ): PreparedRequest {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        val providerId = novel.assistantProviderId ?: novel.bodyProviderId ?: error("请先配置助手模型")
        val modelId = novel.assistantModelId ?: novel.bodyModelId ?: error("请先配置助手模型")
        val adapter = adapters[providerId] ?: error("暂不支持该厂商")
        val existingSession = novelDao.assistantSession(novelId)
        val sessionMessages = existingSession?.messagesJson
            ?.let { runCatching { json.decodeFromString<List<ProviderMessage>>(it) }.getOrDefault(emptyList()) }
            .orEmpty()
        val prepared = contextAssembler.prepareAssistant(
            novelId = novelId,
            currentSessionMessages = sessionMessages,
            newMessage = message,
            providerId = providerId,
            modelId = modelId,
            settings = settings,
            adapter = adapter,
        )
        if (!inspectOnly) executeAssistant(prepared, sessionMessages, message.trim())
        return prepared
    }

    suspend fun sendPreparedAssistant(
        prepared: PreparedRequest,
        previousMessages: List<ProviderMessage>,
        userMessage: String,
    ) {
        require(prepared.taskType == TaskType.ASSISTANT)
        require(userMessage.isNotBlank())
        executeAssistant(prepared, previousMessages, userMessage.trim())
    }

    suspend fun roll(groupId: String) {
        val group = generationDao.candidateGroup(groupId) ?: error("这组候选已经不在了")
        require(
            group.status == CandidateGroupStatus.PENDING ||
                group.status == CandidateGroupStatus.GENERATING ||
                group.status == CandidateGroupStatus.FAILED,
        ) {
            "这组候选已经处理完毕"
        }
        val frozen = generationDao.requestSnapshot(group.frozenRequestSnapshotId) ?: error("没有找到这组候选最初确认的发送内容")
        val prepared = PreparedRequest(
            taskType = frozen.taskType,
            novelId = frozen.novelId,
            targetEntityId = frozen.targetEntityId,
            providerId = frozen.providerId,
            modelId = frozen.modelId,
            logicalContext = json.decodeFromString(frozen.logicalContextJson),
            messages = json.decodeFromString(frozen.finalMessagesJson),
            settings = json.decodeFromString(frozen.generationParametersJson),
            serializedRequestBody = frozen.redactedRequestBody,
            redactedRequestBody = frozen.redactedRequestBody,
        )
        executeExistingGroup(group, prepared)
    }

    suspend fun editCandidate(candidateId: String, text: String) {
        val candidate = generationDao.candidate(candidateId) ?: error("候选不存在")
        val group = generationDao.candidateGroup(candidate.candidateGroupId) ?: error("这组候选已经不在了")
        require(group.status != CandidateGroupStatus.GENERATING) { "请先停止生成再修改候选" }
        check(generationDao.editCandidate(candidateId, text) == 1)
    }

    private suspend fun executeNewGroup(
        prepared: PreparedRequest,
        oneTimeCreativeRequirement: String?,
        summarySourceSnapshotJson: String?,
    ) {
        val request = prepared.toSnapshot(candidateGroupId = null)
        val group = CandidateGroupEntity(
            taskType = prepared.taskType,
            novelId = requireNotNull(prepared.novelId),
            targetChapterId = if (prepared.taskType == TaskType.BODY) prepared.targetEntityId else null,
            oneTimeCreativeRequirement = oneTimeCreativeRequirement,
            summarySourceSnapshotJson = summarySourceSnapshotJson,
            frozenRequestSnapshotId = request.id,
        )
        val linkedRequest = request.copy(candidateGroupId = group.id)
        database.withTransaction {
            generationDao.insertRequestSnapshot(linkedRequest)
            generationDao.insertCandidateGroup(group)
        }
        executeExistingGroup(group, prepared, existingSnapshot = linkedRequest)
    }

    private suspend fun executeAssistant(
        prepared: PreparedRequest,
        previousMessages: List<ProviderMessage>,
        userMessage: String,
    ) {
        val provider = requireProvider(prepared.providerId)
        val apiKey = requireApiKey(provider)
        val adapter = adapters[prepared.providerId] ?: error("暂不支持该厂商")
        val snapshot = prepared.toSnapshot(candidateGroupId = null)
        generationDao.insertRequestSnapshot(snapshot)
        generationDao.markRequestStreaming(snapshot.id)
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var final: StreamResult? = null
        try {
            _progress.emit(GenerationProgress.AssistantStarted)
            adapter.stream(provider.baseUrl, apiKey, prepared).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        content.append(event.content)
                        event.reasoningContent?.let(reasoning::append)
                        _progress.emit(
                            GenerationProgress.AssistantDelta(
                                event.content,
                                event.reasoningContent.takeIf { prepared.settings.streamThinking },
                            ),
                        )
                    }
                    is StreamEvent.Finished -> final = event.result
                }
            }
            val result = final ?: StreamResult(content.toString(), reasoning.toString().ifBlank { null }, false, null, null)
            require(result.content.isNotBlank()) { "助手没有返回可用内容" }
            val updatedMessages = previousMessages +
                ProviderMessage("user", userMessage) +
                ProviderMessage(
                    role = "assistant",
                    content = result.content,
                    reasoningContent = result.reasoningContent,
                    reasoningRequested = prepared.settings.thinkingEnabled,
                )
            database.withTransaction {
                novelDao.upsertAssistantSession(
                    AssistantSessionEntity(
                        novelId = requireNotNull(prepared.novelId),
                        messagesJson = json.encodeToString(updatedMessages),
                        createdAt = novelDao.assistantSession(prepared.novelId)?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                result.usage?.let { normalized ->
                    val modelSetting = modelDao.model(prepared.providerId, prepared.modelId)
                    generationDao.insertUsage(
                        UsageRecordEntity(
                            requestSnapshotId = snapshot.id,
                            novelId = prepared.novelId,
                            taskType = TaskType.ASSISTANT,
                            rawUsageJson = normalized.rawJson,
                            inputTokens = normalized.inputTokens,
                            outputTokens = normalized.outputTokens,
                            totalTokens = normalized.totalTokens,
                            reasoningTokens = normalized.reasoningTokens,
                            cacheTokens = normalized.cacheTokens,
                            completeStatistics = normalized.complete,
                            providerId = prepared.providerId,
                            modelId = prepared.modelId,
                            maxContextTokensSnapshot = modelSetting?.maxContextTokens ?: 1_048_576,
                            maxContextSourceSnapshot = modelSetting?.maxContextSource ?: "default",
                        ),
                    )
                }
                generationDao.finishRequest(
                    snapshot.id,
                    if (result.complete) ResponseStatus.SUCCEEDED else ResponseStatus.PARTIAL,
                    usageMissing = result.usage == null,
                    usageParseErrorJson = result.usageParseError?.let { json.encodeToString(mapOf("message" to it)) },
                )
            }
            _progress.emit(GenerationProgress.AssistantCompleted)
        } catch (error: Exception) {
            generationDao.finishRequest(
                snapshot.id,
                if (content.isNotEmpty()) ResponseStatus.PARTIAL else ResponseStatus.FAILED,
                errorJson = json.encodeToString(mapOf("message" to (error.message ?: "助手请求失败"))),
                usageMissing = true,
            )
            throw error
        }
    }

    private suspend fun executeExistingGroup(
        group: CandidateGroupEntity,
        prepared: PreparedRequest,
        existingSnapshot: RequestSnapshotEntity? = null,
    ) {
        val provider = requireProvider(prepared.providerId)
        val apiKey = requireApiKey(provider)
        val adapter = adapters[prepared.providerId] ?: error("暂不支持该厂商")
        val snapshot = existingSnapshot ?: prepared.toSnapshot(candidateGroupId = group.id).also {
            generationDao.insertRequestSnapshot(it)
        }
        val rollNumber = generationDao.maxRollNumber(group.id) + 1
        val candidate = CandidateEntity(
            candidateGroupId = group.id,
            rollNumber = rollNumber,
            requestSnapshotId = snapshot.id,
            reasoningKind = if (prepared.settings.thinkingEnabled) ReasoningKind.REASONING_CONTENT else ReasoningKind.NONE,
        )
        generationDao.insertCandidate(candidate)
        _activeCandidateId.value = candidate.id
        generationDao.updateGroupState(group.id, CandidateGroupStatus.GENERATING, group.oneTimeCreativeRequirement, null)
        generationDao.markRequestStreaming(snapshot.id)
        _progress.emit(GenerationProgress.Started(group.id, candidate.id))
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var final: StreamResult? = null
        try {
            adapter.stream(provider.baseUrl, apiKey, prepared).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        content.append(event.content)
                        event.reasoningContent?.let(reasoning::append)
                        generationDao.updateCandidateStream(
                            id = candidate.id,
                            rawOutput = content.toString(),
                            editedText = content.toString(),
                            reasoningContent = reasoning.toString().ifBlank { null },
                            complete = false,
                        )
                        _progress.emit(GenerationProgress.Delta(candidate.id, event.content, event.reasoningContent))
                    }

                    is StreamEvent.Finished -> final = event.result
                }
            }
            val result = final ?: StreamResult(
                content = content.toString(),
                reasoningContent = reasoning.toString().ifBlank { null },
                complete = false,
                finishReason = null,
                usage = null,
            )
            val resultContent = result.content.ifBlank { content.toString() }
            val resultReasoning = result.reasoningContent ?: reasoning.toString().ifBlank { null }
            if (resultContent.isBlank() && resultReasoning.isNullOrBlank()) {
                database.withTransaction {
                    generationDao.deleteCandidate(candidate.id)
                    generationDao.finishRequest(
                        snapshot.id,
                        ResponseStatus.FAILED,
                        errorJson = json.encodeToString(mapOf("message" to "模型未返回内容")),
                        usageMissing = true,
                    )
                    generationDao.updateGroupState(group.id, CandidateGroupStatus.FAILED, group.oneTimeCreativeRequirement, null)
                }
                _progress.emit(GenerationProgress.Failed(group.id, null, "模型未返回内容，可直接重试", false))
                return
            }
            database.withTransaction {
                generationDao.updateCandidateStream(
                    candidate.id,
                    resultContent,
                    resultContent,
                    resultReasoning,
                    result.complete,
                )
                val usageRecord = result.usage?.let { normalized ->
                    val modelSetting = modelDao.model(prepared.providerId, prepared.modelId)
                    UsageRecordEntity(
                        requestSnapshotId = snapshot.id,
                        novelId = prepared.novelId,
                        taskType = prepared.taskType,
                        rawUsageJson = normalized.rawJson,
                        inputTokens = normalized.inputTokens,
                        outputTokens = normalized.outputTokens,
                        totalTokens = normalized.totalTokens,
                        reasoningTokens = normalized.reasoningTokens,
                        cacheTokens = normalized.cacheTokens,
                        completeStatistics = normalized.complete,
                        providerId = prepared.providerId,
                        modelId = prepared.modelId,
                        maxContextTokensSnapshot = modelSetting?.maxContextTokens ?: 1_048_576,
                        maxContextSourceSnapshot = modelSetting?.maxContextSource ?: "default",
                    )
                }
                usageRecord?.let {
                    generationDao.insertUsage(it)
                    generationDao.setCandidateUsage(candidate.id, it.id)
                }
                generationDao.finishRequest(
                    snapshot.id,
                    if (result.complete) ResponseStatus.SUCCEEDED else ResponseStatus.PARTIAL,
                    usageMissing = result.usage == null,
                    usageParseErrorJson = result.usageParseError?.let { json.encodeToString(mapOf("message" to it)) },
                )
                generationDao.updateGroupState(group.id, CandidateGroupStatus.PENDING, group.oneTimeCreativeRequirement, null)
            }
            _progress.emit(GenerationProgress.Completed(group.id, candidate.id, result.complete))
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                persistInterrupted(group, candidate, snapshot, content.toString(), reasoning.toString(), "用户停止生成")
            }
            throw error
        } catch (error: Exception) {
            val message = when (error) {
                is ProviderException -> error.message ?: "厂商请求失败"
                else -> error.message?.take(240) ?: "生成失败"
            }
            if (content.isNotEmpty() || reasoning.isNotEmpty()) {
                persistInterrupted(group, candidate, snapshot, content.toString(), reasoning.toString(), message)
                _progress.emit(GenerationProgress.Failed(group.id, candidate.id, message, true))
            } else {
                database.withTransaction {
                    generationDao.deleteCandidate(candidate.id)
                    generationDao.finishRequest(
                        snapshot.id,
                        ResponseStatus.FAILED,
                        errorJson = json.encodeToString(mapOf("message" to message)),
                        usageMissing = true,
                    )
                    generationDao.updateGroupState(group.id, CandidateGroupStatus.FAILED, group.oneTimeCreativeRequirement, null)
                }
                _progress.emit(GenerationProgress.Failed(group.id, null, message, false))
            }
        } finally {
            _activeCandidateId.compareAndSet(candidate.id, null)
        }
    }

    private suspend fun persistInterrupted(
        group: CandidateGroupEntity,
        candidate: CandidateEntity,
        snapshot: RequestSnapshotEntity,
        content: String,
        reasoning: String,
        message: String,
    ) = database.withTransaction {
        generationDao.updateCandidateStream(
            candidate.id,
            content,
            content,
            reasoning.ifBlank { null },
            false,
        )
        generationDao.finishRequest(
            snapshot.id,
            ResponseStatus.PARTIAL,
            errorJson = json.encodeToString(mapOf("message" to message)),
            usageMissing = true,
        )
        generationDao.updateGroupState(group.id, CandidateGroupStatus.PENDING, group.oneTimeCreativeRequirement, null)
    }

    private fun PreparedRequest.toSnapshot(candidateGroupId: String?): RequestSnapshotEntity = RequestSnapshotEntity(
        taskType = taskType,
        novelId = novelId,
        targetEntityId = targetEntityId,
        candidateGroupId = candidateGroupId,
        providerId = providerId,
        modelId = modelId,
        logicalContextJson = json.encodeToString(logicalContext),
        finalMessagesJson = json.encodeToString(messages),
        generationParametersJson = json.encodeToString(settings),
        redactedRequestBody = redactedRequestBody,
    )

    private suspend fun requireProvider(providerId: String): ProviderConfigEntity =
        novelDao.provider(providerId) ?: error("请先配置厂商")

    private fun requireApiKey(provider: ProviderConfigEntity): String =
        secretStore.get(provider.secretReference) ?: error("接口密钥无法读取，请重新保存")
}
