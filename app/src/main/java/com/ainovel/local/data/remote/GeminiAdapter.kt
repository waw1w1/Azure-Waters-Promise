package com.ainovel.local.data.remote

import com.ainovel.local.data.model.TaskType
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiAdapter(
    override val providerId: String = "gemini",
    private val providerDisplayName: String = "Google Gemini",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : ProviderAdapter {

    override suspend fun listModels(baseUrl: String, apiKey: String): List<ProviderModel> = withContext(Dispatchers.IO) {
        if (VertexAiServiceAccountAuth.looksLikeServiceAccountJson(apiKey)) {
            val credentials = try {
                VertexAiServiceAccountAuth.parseServiceAccountJson(apiKey)
            } catch (e: Exception) {
                throw ProviderException(
                    statusCode = 401,
                    retryable = false,
                    message = "服务账号凭证解析失败：${e.message}",
                    cause = e,
                )
            }
            val accessToken = try {
                VertexAiServiceAccountAuth.getAccessToken(credentials, client)
            } catch (e: ProviderException) {
                if (e.statusCode == 400 || e.statusCode == 401 || e.statusCode == 403) {
                    throw ProviderException(statusCode = 401, retryable = false, message = e.message.orEmpty(), cause = e)
                }
                throw e
            } catch (e: Exception) {
                throw ProviderException(
                    statusCode = 401,
                    retryable = false,
                    message = "获取 Google OAuth2 访问令牌失败：${e.message}",
                    cause = e,
                )
            }

            val cleanBaseUrl = baseUrl.trim().trimEnd('/')
            val isCustomHost = !cleanBaseUrl.contains("googleapis.com") && cleanBaseUrl.isNotBlank() &&
                cleanBaseUrl != "https://" && cleanBaseUrl != "http://"

            val candidateEndpoints = if (isCustomHost) {
                listOf(
                    "$cleanBaseUrl/v1beta/models?pageSize=1000",
                    "$cleanBaseUrl/v1beta1/publishers/google/models?pageSize=300",
                )
            } else {
                listOf(
                    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
                    "https://aiplatform.googleapis.com/v1beta1/publishers/google/models?pageSize=300",
                )
            }

            val collectedModels = mutableListOf<ProviderModel>()
            val seenIds = mutableSetOf<String>()

            for (endpoint in candidateEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer $accessToken")
                        .header("x-goog-user-project", credentials.projectId)
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            val parsed = parseGeminiModels(body)
                            for (model in parsed) {
                                if (seenIds.add(model.id)) {
                                collectedModels.add(model)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore individual catalog fetch failures; fall back below if all fail
            }
        }

        if (collectedModels.isNotEmpty()) {
            return@withContext collectedModels
        }

        return@withContext DEFAULT_VERTEX_MODELS
    } else {
        var cleanBase = baseUrl.trim().trimEnd('/')
        for (p in listOf("/v1beta/openai", "/openai", "/v1beta", "/v1")) {
            if (cleanBase.endsWith(p)) {
                cleanBase = cleanBase.removeSuffix(p).trimEnd('/')
                break
            }
        }
        val url = "$cleanBase/v1beta/models?pageSize=1000"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("x-goog-api-key", apiKey)
        if (!cleanBase.contains("generativelanguage.googleapis.com")) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }
        val request = reqBuilder.get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ProviderException(
                retryable = true,
                message = "无法连接 $providerDisplayName：${e.message}",
                cause = e,
            )
        }
        response.use { resp ->
            val body = resp.body.string()
            if (!resp.isSuccessful) throw httpError(resp.code, body, apiKey)
            parseGeminiModels(body)
        }
    }
}

    fun parseGeminiModels(body: String): List<ProviderModel> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        val rawList = root["models"]?.jsonArray
            ?: root["publisherModels"]?.jsonArray
            ?: root["data"]?.jsonArray
            ?: return emptyList()

        return rawList.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val rawName = obj["name"]?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val id = VertexAiServiceAccountAuth.normalizeVertexModelId(rawName)
            if (id.isBlank()) return@mapNotNull null

            val lower = id.lowercase()

            val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }
            if (methods != null && methods.isNotEmpty() && !methods.contains("generateContent")) {
                return@mapNotNull null
            }

            val isExcluded = lower.contains("embedding") ||
                lower.contains("imagen") ||
                lower.contains("veo") ||
                lower.contains("audio") ||
                lower.contains("tts") ||
                lower.contains("whisper") ||
                lower.contains("aqa") ||
                lower.contains("robotics")
            if (isExcluded) return@mapNotNull null

            if (methods == null) {
                val isKnownModelFamily = lower.startsWith("gemini") ||
                    lower.startsWith("gemma") ||
                    lower.contains("text") ||
                    lower.contains("chat")
                if (!isKnownModelFamily) return@mapNotNull null
            }

            val hasThinking = obj["thinking"]?.jsonPrimitive?.booleanOrNull == true ||
                lower.contains("thinking") ||
                lower.contains("reason") ||
                lower.startsWith("gemini-2.5") ||
                lower.startsWith("gemini-3") ||
                lower.startsWith("gemini-4")

            val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: id

            ProviderModel(
                id = id,
                displayName = displayName,
                supportsTextGeneration = true,
                supportsStreaming = true,
                supportsReasoning = hasThinking,
                supportsReasoningStrength = hasThinking,
            )
        }
    }

    override fun prepare(
        taskType: TaskType,
        novelId: String?,
        targetEntityId: String?,
        modelId: String,
        logicalContext: List<LogicalContextItem>,
        messages: List<ProviderMessage>,
        settings: GenerationSettings,
    ): PreparedRequest {
        require(modelId.isNotBlank()) { "请选择模型" }
        require(messages.isNotEmpty()) { "请求消息不能为空" }

        val cleanModel = VertexAiServiceAccountAuth.normalizeVertexModelId(modelId)
        val nonSystem = messages.filterNot { it.role == "system" }
        require(nonSystem.isNotEmpty()) { "请求消息不能为空" }

        data class Turn(val role: String, val texts: MutableList<String>)
        val turns = mutableListOf<Turn>()
        for (msg in nonSystem) {
            val role = if (msg.role == "assistant") "model" else "user"
            if (turns.isNotEmpty() && turns.last().role == role) {
                turns.last().texts.add(msg.content)
            } else {
                turns.add(Turn(role, mutableListOf(msg.content)))
            }
        }

        val systemMessages = messages.filter { it.role == "system" }
        val systemText = systemMessages.joinToString("\n\n") { it.content }.trim()

        val modernGeminiRegex = Regex("""^gemini-(?:[3-9]|\d{2,})(?:[.-].*|$)""", RegexOption.IGNORE_CASE)
        val isModernGemini = cleanModel.startsWith("gemini-3") ||
            cleanModel.startsWith("gemini-4") ||
            modernGeminiRegex.matches(cleanModel)
        val isReasoningModel = isModernGemini || cleanModel.startsWith("gemini-2.5") || cleanModel.contains("thinking")

        val body = buildJsonObject {
            put("contents", buildJsonArray {
                if (turns.isNotEmpty() && turns.first().role == "model") {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", " ") })
                        })
                    })
                }
                turns.forEach { turn ->
                    add(buildJsonObject {
                        put("role", turn.role)
                        put("parts", buildJsonArray {
                            turn.texts.forEach { text ->
                                add(buildJsonObject { put("text", text) })
                            }
                        })
                    })
                }
            })

            if (systemText.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
            }

            put("generationConfig", buildJsonObject {
                settings.maxOutputTokens?.let { put("maxOutputTokens", it) }
                if (!settings.thinkingEnabled || !isReasoningModel) {
                    settings.temperature?.let { put("temperature", it) }
                }
                if (isReasoningModel) {
                    put("thinkingConfig", buildJsonObject {
                        if (isModernGemini) {
                            if (!settings.thinkingEnabled) {
                                put("includeThoughts", false)
                            } else {
                                put("includeThoughts", true)
                                val level = when (settings.reasoningStrength) {
                                    ReasoningStrength.WEAK -> "LOW"
                                    ReasoningStrength.MEDIUM -> "MEDIUM"
                                    ReasoningStrength.STRONG -> "HIGH"
                                    ReasoningStrength.UNSUPPORTED -> null
                                }
                                if (level != null) {
                                    put("thinkingLevel", level)
                                }
                            }
                        } else {
                            if (!settings.thinkingEnabled) {
                                put("includeThoughts", false)
                                if (!cleanModel.contains("gemini-2.5-pro")) {
                                    put("thinkingBudget", 0)
                                }
                            } else {
                                put("includeThoughts", true)
                                val budget = when (settings.reasoningStrength) {
                                    ReasoningStrength.WEAK -> 1024
                                    ReasoningStrength.STRONG -> 16384
                                    ReasoningStrength.MEDIUM, ReasoningStrength.UNSUPPORTED -> 4096
                                }
                                put("thinkingBudget", budget)
                            }
                        }
                    })
                }
            })
        }

        val serialized = json.encodeToString(JsonObject.serializer(), body)
        return PreparedRequest(
            taskType = taskType,
            novelId = novelId,
            targetEntityId = targetEntityId,
            providerId = providerId,
            modelId = modelId,
            logicalContext = logicalContext,
            messages = messages,
            settings = settings,
            serializedRequestBody = serialized,
            redactedRequestBody = serialized,
        )
    }

    override fun stream(
        baseUrl: String,
        apiKey: String,
        preparedRequest: PreparedRequest,
    ): Flow<StreamEvent> = flow {
        val isServiceAccount = VertexAiServiceAccountAuth.looksLikeServiceAccountJson(apiKey)
        val saCredentials = if (isServiceAccount) {
            try {
                VertexAiServiceAccountAuth.parseServiceAccountJson(apiKey)
            } catch (e: Exception) {
                throw ProviderException(
                    statusCode = 401,
                    retryable = false,
                    message = "服务账号凭证解析失败：${e.message}",
                    cause = e,
                )
            }
        } else null

        val requestUrl = VertexAiServiceAccountAuth.buildGenerateContentUrl(
            baseUrl = baseUrl,
            model = preparedRequest.modelId,
            stream = preparedRequest.settings.streamOutput,
            credentials = saCredentials,
        )

        val reqBuilder = Request.Builder()
            .url(requestUrl)
            .header("Accept", if (preparedRequest.settings.streamOutput) "text/event-stream" else "application/json")
            .post(preparedRequest.serializedRequestBody.toRequestBody(JSON_MEDIA_TYPE))

        if (saCredentials != null) {
            val accessToken = try {
                VertexAiServiceAccountAuth.getAccessToken(saCredentials, client)
            } catch (e: ProviderException) {
                if (e.statusCode == 400 || e.statusCode == 401 || e.statusCode == 403) {
                    throw ProviderException(
                        statusCode = 401,
                        retryable = false,
                        responseBody = e.responseBody,
                        message = e.message.orEmpty(),
                        cause = e,
                    )
                }
                throw e
            } catch (e: Exception) {
                throw ProviderException(
                    statusCode = 401,
                    retryable = false,
                    message = "获取 Google OAuth2 访问令牌失败：${e.message}",
                    cause = e,
                )
            }
            reqBuilder.header("Authorization", "Bearer $accessToken")
            reqBuilder.header("x-goog-user-project", saCredentials.projectId)
        } else {
            reqBuilder.header("x-goog-api-key", apiKey)
            if (!baseUrl.contains("generativelanguage.googleapis.com")) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }
        }

        val call = client.newCall(reqBuilder.build())
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    throw httpError(response.code, errorBody, apiKey)
                }

                if (preparedRequest.settings.streamOutput) {
                    val fullContent = StringBuilder()
                    val fullReasoning = StringBuilder()
                    var finishReason: String? = null
                    var normalizedUsage: NormalizedUsage? = null
                    val source = response.body.source()

                    while (!source.exhausted()) {
                        currentCoroutineContext().ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val raw = line.removePrefix("data:").trim()
                        if (raw.isBlank() || raw == "[DONE]") continue

                        val root = try {
                            json.parseToJsonElement(raw).jsonObject
                        } catch (_: Exception) {
                            continue
                        }

                        val blockReason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
                        val candidates = root["candidates"]?.jsonArray.orEmpty()
                        if (candidates.isEmpty() && blockReason != null) {
                            throw ProviderException(
                                statusCode = 400,
                                retryable = false,
                                message = "Gemini 提示词被拦截，原因：$blockReason",
                            )
                        }

                        root["usageMetadata"]?.jsonObject?.let { usageObj ->
                            val promptTokens = usageObj["promptTokenCount"]?.jsonPrimitive?.longOrNull ?: 0L
                            val candidateTokens = usageObj["candidatesTokenCount"]?.jsonPrimitive?.longOrNull ?: 0L
                            val totalTokens = usageObj["totalTokenCount"]?.jsonPrimitive?.longOrNull ?: (promptTokens + candidateTokens)
                            val thoughtsTokens = usageObj["thoughtsTokenCount"]?.jsonPrimitive?.longOrNull
                            val cachedTokens = usageObj["cachedContentTokenCount"]?.jsonPrimitive?.longOrNull
                            normalizedUsage = NormalizedUsage(
                                rawJson = json.encodeToString(JsonObject.serializer(), usageObj),
                                inputTokens = promptTokens,
                                outputTokens = candidateTokens,
                                totalTokens = totalTokens,
                                reasoningTokens = thoughtsTokens,
                                cacheTokens = cachedTokens,
                                complete = true,
                            )
                        }

                        if (candidates.isNotEmpty()) {
                            val firstCandidate = candidates[0].jsonObject
                            firstCandidate["finishReason"]?.jsonPrimitive?.contentOrNull?.let {
                                finishReason = it
                            }

                            val parts = firstCandidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                            val deltaContent = StringBuilder()
                            val deltaReasoning = StringBuilder()

                            for (part in parts) {
                                val partObj = part.jsonObject
                                val text = partObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val isThought = partObj["thought"]?.jsonPrimitive?.booleanOrNull == true
                                if (isThought) {
                                    deltaReasoning.append(text)
                                } else {
                                    deltaContent.append(text)
                                }
                            }

                            val dC = deltaContent.toString()
                            val dR = deltaReasoning.toString()
                            if (dC.isNotEmpty() || dR.isNotEmpty()) {
                                fullContent.append(dC)
                                fullReasoning.append(dR)
                                emit(
                                    StreamEvent.Delta(
                                        content = dC,
                                        reasoningContent = dR.takeIf { it.isNotEmpty() },
                                    )
                                )
                            }
                        }
                    }

                    val finalReasoning = fullReasoning.toString().takeIf { it.isNotEmpty() }
                    emit(
                        StreamEvent.Finished(
                            StreamResult(
                                content = fullContent.toString(),
                                reasoningContent = finalReasoning,
                                complete = finishReason != null,
                                finishReason = finishReason,
                                usage = normalizedUsage,
                                usageParseError = if (normalizedUsage == null) "厂商未返回完整 usage" else null,
                            )
                        )
                    )
                } else {
                    val rawBody = response.body.string()
                    val root = try {
                        json.parseToJsonElement(rawBody).jsonObject
                    } catch (e: Exception) {
                        throw ProviderException(
                            statusCode = 500,
                            retryable = false,
                            message = "Gemini 返回的不是合法的 JSON：${e.message}",
                            cause = e,
                        )
                    }

                    val blockReason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
                    val candidates = root["candidates"]?.jsonArray.orEmpty()
                    if (candidates.isEmpty() && blockReason != null) {
                        throw ProviderException(
                            statusCode = 400,
                            retryable = false,
                            message = "Gemini 提示词被拦截，原因：$blockReason",
                        )
                    }

                    var normalizedUsage: NormalizedUsage? = null
                    root["usageMetadata"]?.jsonObject?.let { usageObj ->
                        val promptTokens = usageObj["promptTokenCount"]?.jsonPrimitive?.longOrNull ?: 0L
                        val candidateTokens = usageObj["candidatesTokenCount"]?.jsonPrimitive?.longOrNull ?: 0L
                        val totalTokens = usageObj["totalTokenCount"]?.jsonPrimitive?.longOrNull ?: (promptTokens + candidateTokens)
                        val thoughtsTokens = usageObj["thoughtsTokenCount"]?.jsonPrimitive?.longOrNull
                        val cachedTokens = usageObj["cachedContentTokenCount"]?.jsonPrimitive?.longOrNull
                        normalizedUsage = NormalizedUsage(
                            rawJson = json.encodeToString(JsonObject.serializer(), usageObj),
                            inputTokens = promptTokens,
                            outputTokens = candidateTokens,
                            totalTokens = totalTokens,
                            reasoningTokens = thoughtsTokens,
                            cacheTokens = cachedTokens,
                            complete = true,
                        )
                    }

                    val fullContent = StringBuilder()
                    val fullReasoning = StringBuilder()
                    var finishReason: String? = null

                    if (candidates.isNotEmpty()) {
                        val firstCandidate = candidates[0].jsonObject
                        finishReason = firstCandidate["finishReason"]?.jsonPrimitive?.contentOrNull
                        val parts = firstCandidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                        for (part in parts) {
                            val partObj = part.jsonObject
                            val text = partObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val isThought = partObj["thought"]?.jsonPrimitive?.booleanOrNull == true
                            if (isThought) {
                                fullReasoning.append(text)
                            } else {
                                fullContent.append(text)
                            }
                        }
                    }

                    val content = fullContent.toString()
                    val reasoning = fullReasoning.toString().takeIf { it.isNotEmpty() }
                    if (content.isNotEmpty() || reasoning != null) {
                        emit(StreamEvent.Delta(content = content, reasoningContent = reasoning))
                    }
                    emit(
                        StreamEvent.Finished(
                            StreamResult(
                                content = content,
                                reasoningContent = reasoning,
                                complete = finishReason != null,
                                finishReason = finishReason,
                                usage = normalizedUsage,
                                usageParseError = if (normalizedUsage == null) "厂商未返回完整 usage" else null,
                            )
                        )
                    )
                }
            }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        } catch (error: ProviderException) {
            throw error
        } catch (error: IOException) {
            throw ProviderException(
                retryable = true,
                message = "$providerDisplayName 连接中断：${error.message.orEmpty()}",
                cause = error,
            )
        } finally {
            if (!call.isExecuted()) call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun sanitizeError(body: String, apiKey: String): String {
        var safe = body
        if (apiKey.isNotBlank() && !apiKey.startsWith("{")) {
            safe = safe.replace(apiKey, "***")
        }
        safe = safe.replace(
            Regex("""-----BEGIN[A-Z ]*PRIVATE KEY-----.*?-----END[A-Z ]*PRIVATE KEY-----""", RegexOption.DOT_MATCHES_ALL),
            "[REDACTED_PRIVATE_KEY]",
        )
        if (apiKey.contains("private_key")) {
            safe = safe.replace(Regex(""""private_key"\s*:\s*"[^"]*""""), """"private_key": "[REDACTED]"""")
        }
        return safe
    }

    private fun httpError(status: Int, body: String, apiKey: String): ProviderException {
        val safe = sanitizeError(body, apiKey)
        return ProviderException(
            statusCode = status,
            retryable = status == 408 || status == 429 || status >= 500,
            responseBody = safe.take(4_000),
            message = when (status) {
                401, 403 -> "接口密钥或服务账号无权限（HTTP $status）"
                404 -> "接口地址或模型不存在（HTTP 404）"
                408, 429 -> "请求过于频繁或暂时超时（HTTP $status）"
                else -> "$providerDisplayName 请求失败（HTTP $status）：${safe.take(300)}"
            },
        )
    }

    companion object {
        val DEFAULT_VERTEX_MODELS = listOf(
            ProviderModel(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                supportsTextGeneration = true,
                supportsStreaming = true,
                supportsReasoning = true,
                supportsReasoningStrength = true,
            ),
            ProviderModel(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                supportsTextGeneration = true,
                supportsStreaming = true,
                supportsReasoning = true,
                supportsReasoningStrength = true,
            ),
            ProviderModel(
                id = "gemini-3.8-flash-tiered",
                displayName = "Gemini 3.8 Flash (Tiered)",
                supportsTextGeneration = true,
                supportsStreaming = true,
                supportsReasoning = true,
                supportsReasoningStrength = true,
            ),
            ProviderModel(
                id = "gemini-4-argon",
                displayName = "Gemini 4 Argon",
                supportsTextGeneration = true,
                supportsStreaming = true,
                supportsReasoning = true,
                supportsReasoningStrength = true,
            ),
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
