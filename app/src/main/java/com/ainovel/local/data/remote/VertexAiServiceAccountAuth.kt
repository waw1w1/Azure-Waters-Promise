package com.ainovel.local.data.remote

import java.io.IOException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

typealias ServiceAccountCredentials = VertexAiServiceAccountAuth.ServiceAccountCredentials

object VertexAiServiceAccountAuth {
    const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"

    val VERTEX_OAUTH_SCOPES = listOf(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/generative-language.retriever",
    )

    data class ServiceAccountCredentials(
        val projectId: String,
        val clientEmail: String,
        val privateKeyPem: String,
        val privateKeyId: String? = null,
        val tokenUri: String = DEFAULT_TOKEN_URI,
        val defaultLocation: String = "global",
    )

    private val defaultJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private data class CachedToken(val token: String, val expiresAtMillis: Long)
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    fun looksLikeServiceAccountJson(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim().removePrefix("\uFEFF").trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val element = defaultJson.parseToJsonElement(trimmed)
            element is JsonObject
        } catch (_: Exception) {
            false
        }
    }

    fun parseServiceAccountJson(raw: String): ServiceAccountCredentials {
        val trimmed = raw.trim().removePrefix("\uFEFF").trim()
        val element = try {
            defaultJson.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("服务账号 JSON 格式错误：${e.message}", e)
        }
        val obj = element as? JsonObject ?: throw IllegalArgumentException("服务账号内容必须为 JSON 对象")
        if (obj.containsKey("web") || obj.containsKey("installed")) {
            throw IllegalStateException("检测到 OAuth 客户端 JSON 而非服务账号（Service Account）密钥 JSON，请导入包含 private_key 与 client_email 的服务账号 JSON")
        }
        val projectId = obj["project_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val clientEmail = obj["client_email"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val privateKey = obj["private_key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val privateKeyId = obj["private_key_id"]?.jsonPrimitive?.contentOrNull?.trim()
        val tokenUri = obj["token_uri"]?.jsonPrimitive?.contentOrNull?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_TOKEN_URI
        val location = (obj["location"] ?: obj["region"])?.jsonPrimitive?.contentOrNull?.trim().takeUnless { it.isNullOrBlank() } ?: "global"

        if (projectId.isBlank()) {
            throw IllegalArgumentException("服务账号 JSON 缺少 project_id")
        }
        if (clientEmail.isBlank()) {
            throw IllegalArgumentException("服务账号 JSON 缺少 client_email")
        }
        if (privateKey.isBlank() || !privateKey.contains("PRIVATE KEY")) {
            throw IllegalArgumentException("服务账号 JSON 缺少有效的 private_key（需包含 PEM 格式私钥）")
        }

        return ServiceAccountCredentials(
            projectId = projectId,
            clientEmail = clientEmail,
            privateKeyPem = privateKey,
            privateKeyId = privateKeyId,
            tokenUri = tokenUri,
            defaultLocation = location,
        )
    }

    fun vertexHostForLocation(location: String): String {
        val loc = location.trim().lowercase()
        return when (loc) {
            "global", "us" -> "aiplatform.googleapis.com"
            "eu" -> "aiplatform.eu.rep.googleapis.com"
            else -> "$loc-aiplatform.googleapis.com"
        }
    }

    fun buildDefaultVertexBaseUrl(projectId: String, location: String = "global"): String {
        val loc = location.trim().ifBlank { "global" }
        val host = vertexHostForLocation(loc)
        return "https://$host/v1/projects/${projectId.trim()}/locations/$loc"
    }

    fun resolveLocation(baseUrl: String, defaultLocation: String = "global"): String {
        val trimmed = baseUrl.trim()
        val locMatch = Regex("""/locations/([^/?#]+)""").find(trimmed)
        if (locMatch != null) {
            val loc = locMatch.groupValues[1].trim()
            if (loc.isNotBlank()) return loc
        }
        if (trimmed.contains("aiplatform.eu.rep.googleapis.com")) {
            return "eu"
        }
        val hostMatch = Regex("""(?:https?://)?([a-z0-9-]+)-aiplatform\.googleapis\.com""").find(trimmed)
        if (hostMatch != null) {
            val loc = hostMatch.groupValues[1].trim()
            if (loc.isNotBlank() && loc != "aiplatform") return loc
        }
        return defaultLocation.trim().ifBlank { "global" }
    }

    fun resolveProjectId(baseUrl: String, fallbackProjectId: String): String {
        val trimmed = baseUrl.trim()
        val projMatch = Regex("""/projects/([^/?#]+)""").find(trimmed)
        if (projMatch != null) {
            val proj = projMatch.groupValues[1].trim()
            if (proj.isNotBlank()) return proj
        }
        return fallbackProjectId.trim()
    }

    fun normalizeVertexModelId(model: String): String {
        var m = model.trim()
        val projectsPrefix = Regex("""^projects/[^/]+/locations/[^/]+/publishers/google/models/""")
        m = m.replace(projectsPrefix, "")
        if (m.startsWith("publishers/google/models/")) {
            m = m.removePrefix("publishers/google/models/")
        }
        if (m.startsWith("models/")) {
            m = m.removePrefix("models/")
        }
        if (m.startsWith("google/")) {
            m = m.removePrefix("google/")
        }
        return m
    }

    fun buildGenerateContentUrl(
        baseUrl: String,
        model: String,
        stream: Boolean,
        credentials: ServiceAccountCredentials? = null,
    ): String {
        val action = if (stream) ":streamGenerateContent?alt=sse" else ":generateContent"
        val cleanModel = normalizeVertexModelId(model)
        val trimmed = baseUrl.trim().trimEnd('/')

        if (trimmed.contains("/publishers/google/models")) {
            return "$trimmed/$cleanModel$action"
        }

        if (trimmed.contains("/projects/") && trimmed.contains("/locations/")) {
            return "$trimmed/publishers/google/models/$cleanModel$action"
        }

        if (credentials != null) {
            val location = resolveLocation(trimmed, credentials.defaultLocation)
            val projectId = resolveProjectId(trimmed, credentials.projectId)
            val version = if (trimmed.contains("/v1beta1")) "v1beta1" else "v1"

            val isGoogleHost = trimmed.contains("googleapis.com", ignoreCase = true)
            if (isGoogleHost || trimmed.isBlank() || trimmed == "https://" || trimmed == "http://") {
                val host = vertexHostForLocation(location)
                return "https://$host/$version/projects/$projectId/locations/$location/publishers/google/models/$cleanModel$action"
            }

            return "$trimmed/$version/projects/$projectId/locations/$location/publishers/google/models/$cleanModel$action"
        }

        if (trimmed.contains("aiplatform.googleapis.com") || trimmed.contains("aiplatform.eu.rep.googleapis.com")) {
            val location = resolveLocation(trimmed, "global")
            val projectId = resolveProjectId(trimmed, "")
            if (projectId.isNotBlank()) {
                val host = vertexHostForLocation(location)
                val version = if (trimmed.contains("/v1beta1")) "v1beta1" else "v1"
                return "https://$host/$version/projects/$projectId/locations/$location/publishers/google/models/$cleanModel$action"
            }
        }

        var cleanBase = trimmed
        for (p in listOf("/v1beta/openai", "/openai", "/v1beta", "/v1")) {
            if (cleanBase.endsWith(p)) {
                cleanBase = cleanBase.removeSuffix(p).trimEnd('/')
                break
            }
        }
        return "$cleanBase/v1beta/models/$cleanModel$action"
    }

    fun buildStreamGenerateContentUrl(
        baseUrl: String,
        model: String,
        credentials: ServiceAccountCredentials? = null,
    ): String = buildGenerateContentUrl(baseUrl, model, stream = true, credentials = credentials)

    internal fun createSignedJwt(
        credentials: ServiceAccountCredentials,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): String {
        val headerObj = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
            credentials.privateKeyId?.takeIf { it.isNotBlank() }?.let {
                put("kid", it)
            }
        }

        val claimsObj = buildJsonObject {
            put("iss", credentials.clientEmail)
            put("sub", credentials.clientEmail)
            put("aud", credentials.tokenUri)
            put("iat", nowEpochSeconds)
            put("exp", nowEpochSeconds + 3600L)
            put("scope", VERTEX_OAUTH_SCOPES.joinToString(" "))
        }

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encodedHeader = encoder.encodeToString(headerObj.toString().toByteArray(Charsets.UTF_8))
        val encodedClaims = encoder.encodeToString(claimsObj.toString().toByteArray(Charsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedClaims"

        val cleanedKey = credentials.privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = try {
            Base64.getDecoder().decode(cleanedKey)
        } catch (e: Exception) {
            throw IllegalArgumentException("解析 RSA 私钥 Base64 失败：${e.message}", e)
        }

        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray(Charsets.UTF_8))
        val signatureBytes = signature.sign()
        val encodedSignature = encoder.encodeToString(signatureBytes)

        return "$signingInput.$encodedSignature"
    }

    internal fun clearCacheForTests() {
        tokenCache.clear()
    }

    fun getAccessToken(
        credentials: ServiceAccountCredentials,
        client: OkHttpClient,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val cacheKey = "${credentials.clientEmail}#${credentials.tokenUri}"
        val cached = tokenCache[cacheKey]
        if (cached != null && cached.expiresAtMillis - nowMillis > 60_000L) {
            return cached.token
        }

        val jwt = try {
            createSignedJwt(credentials, nowEpochSeconds = nowMillis / 1000L)
        } catch (e: Exception) {
            throw ProviderException(
                statusCode = 401,
                retryable = false,
                message = "服务账号私钥解析或签名失败：${e.message}",
                cause = e,
            )
        }
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder()
            .url(credentials.tokenUri)
            .header("Accept", "application/json")
            .post(formBody)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ProviderException(
                statusCode = 401,
                retryable = true,
                message = "无法连接 Google OAuth2 服务：${e.message}",
                cause = e,
            )
        }

        return response.use { resp ->
            val rawBody = resp.body.string()
            if (!resp.isSuccessful) {
                val safe = sanitizeBody(rawBody, credentials)
                val isAuthFailure = resp.code == 400 || resp.code == 401 || resp.code == 403
                throw ProviderException(
                    statusCode = if (isAuthFailure) 401 else resp.code,
                    retryable = if (isAuthFailure) false else (resp.code == 408 || resp.code == 429 || resp.code >= 500),
                    responseBody = safe.take(4_000),
                    message = "Google 服务账号认证失败（HTTP ${resp.code}）：$safe",
                )
            }
            val element = try {
                defaultJson.parseToJsonElement(rawBody).jsonObject
            } catch (e: Exception) {
                throw ProviderException(
                    statusCode = 500,
                    retryable = false,
                    message = "Google OAuth2 返回的不是合法的 JSON：${e.message}",
                    cause = e,
                )
            }
            val accessToken = element["access_token"]?.jsonPrimitive?.contentOrNull
                ?: throw ProviderException(
                    statusCode = 401,
                    retryable = false,
                    message = "Google OAuth2 返回缺失 access_token",
                )
            val expiresIn = element["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
            val ttlMillis = expiresIn.coerceAtLeast(60L) * 1000L
            val expiresAt = nowMillis + ttlMillis
            tokenCache[cacheKey] = CachedToken(accessToken, expiresAt)
            accessToken
        }
    }

    private fun sanitizeBody(body: String, credentials: ServiceAccountCredentials): String {
        var safe = body
        if (credentials.privateKeyPem.isNotBlank()) {
            safe = safe.replace(
                Regex("""-----BEGIN[A-Z ]*PRIVATE KEY-----.*?-----END[A-Z ]*PRIVATE KEY-----""", RegexOption.DOT_MATCHES_ALL),
                "[REDACTED_PRIVATE_KEY]",
            )
        }
        return safe
    }
}
