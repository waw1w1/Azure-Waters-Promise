package com.ainovel.local.data.remote

import com.ainovel.local.data.model.TaskType
import com.ainovel.local.domain.GenerationRepository
import com.ainovel.local.ui.localization.AppLanguage
import com.ainovel.local.ui.localization.localized
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VertexAiAndGeminiAdapterTest {

    private lateinit var testKeyPair: KeyPair
    private lateinit var testPrivateKeyPem: String
    private lateinit var testSaJson: String
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        VertexAiServiceAccountAuth.clearCacheForTests()
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        testKeyPair = keyGen.generateKeyPair()
        testPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder().encodeToString(testKeyPair.private.encoded) +
            "\n-----END PRIVATE KEY-----"
        testSaJson = """
            {
              "type": "service_account",
              "project_id": "test-project-123",
              "private_key_id": "key-id-abc",
              "private_key": "${testPrivateKeyPem.replace("\n", "\\n")}",
              "client_email": "sa-test@test-project-123.iam.gserviceaccount.com",
              "client_id": "1234567890",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token",
              "location": "global"
            }
        """.trimIndent()
    }

    @After
    fun tearDown() {
        VertexAiServiceAccountAuth.clearCacheForTests()
    }

    @Test
    fun testRS256JwtSigningAndVerification() {
        val credentials = VertexAiServiceAccountAuth.ServiceAccountCredentials(
            projectId = "test-project-123",
            clientEmail = "sa-test@test-project-123.iam.gserviceaccount.com",
            privateKeyPem = testPrivateKeyPem,
            privateKeyId = "key-id-abc",
            tokenUri = "https://oauth2.googleapis.com/token",
            defaultLocation = "global",
        )
        val now = 1700000000L
        val jwt = VertexAiServiceAccountAuth.createSignedJwt(credentials, now)
        val parts = jwt.split(".")
        assertEquals(3, parts.size)

        // Verify header
        val headerJson = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        val header = json.parseToJsonElement(headerJson).jsonObject
        assertEquals("RS256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
        assertEquals("key-id-abc", header["kid"]?.jsonPrimitive?.content)

        // Verify claims
        val claimsJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val claims = json.parseToJsonElement(claimsJson).jsonObject
        assertEquals("sa-test@test-project-123.iam.gserviceaccount.com", claims["iss"]?.jsonPrimitive?.content)
        assertEquals("sa-test@test-project-123.iam.gserviceaccount.com", claims["sub"]?.jsonPrimitive?.content)
        assertEquals("https://oauth2.googleapis.com/token", claims["aud"]?.jsonPrimitive?.content)
        assertEquals(now.toString(), claims["iat"]?.jsonPrimitive?.content)
        assertEquals((now + 3600L).toString(), claims["exp"]?.jsonPrimitive?.content)
        val scope = claims["scope"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(scope.contains("https://www.googleapis.com/auth/cloud-platform"))
        assertTrue(scope.contains("https://www.googleapis.com/auth/generative-language.retriever"))

        // Verify signature with RSA public key
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(testKeyPair.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
        val signatureBytes = Base64.getUrlDecoder().decode(parts[2])
        assertTrue(verifier.verify(signatureBytes))

        // Verify tampering fails
        verifier.initVerify(testKeyPair.public)
        verifier.update("${parts[0]}.${parts[1]}tampered".toByteArray(Charsets.UTF_8))
        assertFalse(verifier.verify(signatureBytes))
    }

    @Test
    fun testServiceAccountJsonValidation() {
        // Valid Service Account JSON
        val creds = VertexAiServiceAccountAuth.parseServiceAccountJson(testSaJson)
        assertEquals("test-project-123", creds.projectId)
        assertEquals("sa-test@test-project-123.iam.gserviceaccount.com", creds.clientEmail)
        assertEquals("key-id-abc", creds.privateKeyId)
        assertEquals("global", creds.defaultLocation)

        // OAuth Client JSON rejection (web)
        val oauthWebJson = """{ "web": { "client_id": "123", "client_secret": "456" } }"""
        try {
            VertexAiServiceAccountAuth.parseServiceAccountJson(oauthWebJson)
            fail("Expected IllegalStateException for web oauth client json")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("检测到 OAuth 客户端 JSON 而非服务账号"))
        }

        // OAuth Client JSON rejection (installed)
        val oauthInstalledJson = """{ "installed": { "client_id": "123", "client_secret": "456" } }"""
        try {
            VertexAiServiceAccountAuth.parseServiceAccountJson(oauthInstalledJson)
            fail("Expected IllegalStateException for installed oauth client json")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("检测到 OAuth 客户端 JSON 而非服务账号"))
        }

        // Missing project_id
        val missingProjectJson = """{ "client_email": "a@b.com", "private_key": "$testPrivateKeyPem" }"""
        try {
            VertexAiServiceAccountAuth.parseServiceAccountJson(missingProjectJson)
            fail("Expected IllegalArgumentException for missing project_id")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("缺少 project_id"))
        }

        // Missing client_email
        val missingEmailJson = """{ "project_id": "p1", "private_key": "$testPrivateKeyPem" }"""
        try {
            VertexAiServiceAccountAuth.parseServiceAccountJson(missingEmailJson)
            fail("Expected IllegalArgumentException for missing client_email")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("缺少 client_email"))
        }

        // Missing private_key
        val missingKeyJson = """{ "project_id": "p1", "client_email": "a@b.com" }"""
        try {
            VertexAiServiceAccountAuth.parseServiceAccountJson(missingKeyJson)
            fail("Expected IllegalArgumentException for missing private_key")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("缺少有效的 private_key"))
        }

        // looksLikeServiceAccountJson checks
        assertTrue(VertexAiServiceAccountAuth.looksLikeServiceAccountJson(testSaJson))
        assertTrue(VertexAiServiceAccountAuth.looksLikeServiceAccountJson(oauthWebJson))
        assertFalse(VertexAiServiceAccountAuth.looksLikeServiceAccountJson("AIzaSyB12345abcdef"))
        assertFalse(VertexAiServiceAccountAuth.looksLikeServiceAccountJson(""))
        assertFalse(VertexAiServiceAccountAuth.looksLikeServiceAccountJson(null))
        assertFalse(VertexAiServiceAccountAuth.looksLikeServiceAccountJson("{ invalid json }"))

        // UTF-8 BOM handling
        val bomSaJson = "\uFEFF" + testSaJson
        assertTrue(VertexAiServiceAccountAuth.looksLikeServiceAccountJson(bomSaJson))
        val bomCreds = VertexAiServiceAccountAuth.parseServiceAccountJson(bomSaJson)
        assertEquals("test-project-123", bomCreds.projectId)
    }

    @Test
    fun testVertexAiUrlResolution() {
        assertEquals("aiplatform.googleapis.com", VertexAiServiceAccountAuth.vertexHostForLocation("global"))
        assertEquals("aiplatform.googleapis.com", VertexAiServiceAccountAuth.vertexHostForLocation("us"))
        assertEquals("aiplatform.eu.rep.googleapis.com", VertexAiServiceAccountAuth.vertexHostForLocation("eu"))
        assertEquals("us-central1-aiplatform.googleapis.com", VertexAiServiceAccountAuth.vertexHostForLocation("us-central1"))
        assertEquals("asia-east1-aiplatform.googleapis.com", VertexAiServiceAccountAuth.vertexHostForLocation("asia-east1"))

        assertEquals(
            "https://aiplatform.googleapis.com/v1/projects/my-proj/locations/global",
            VertexAiServiceAccountAuth.buildDefaultVertexBaseUrl("my-proj", "global"),
        )
        assertEquals(
            "https://us-central1-aiplatform.googleapis.com/v1/projects/my-proj/locations/us-central1",
            VertexAiServiceAccountAuth.buildDefaultVertexBaseUrl("my-proj", "us-central1"),
        )
        assertEquals(
            "https://aiplatform.eu.rep.googleapis.com/v1/projects/my-proj/locations/eu",
            VertexAiServiceAccountAuth.buildDefaultVertexBaseUrl("my-proj", "eu"),
        )

        // Model ID normalization
        assertEquals("gemini-2.5-pro", VertexAiServiceAccountAuth.normalizeVertexModelId("models/gemini-2.5-pro"))
        assertEquals("gemini-2.5-flash", VertexAiServiceAccountAuth.normalizeVertexModelId("publishers/google/models/gemini-2.5-flash"))
        assertEquals("gemini-3.8-flash-tiered", VertexAiServiceAccountAuth.normalizeVertexModelId("projects/p/locations/l/publishers/google/models/gemini-3.8-flash-tiered"))
        assertEquals("gemini-2.5-flash", VertexAiServiceAccountAuth.normalizeVertexModelId("google/gemini-2.5-flash"))

        val credsGlobal = VertexAiServiceAccountAuth.ServiceAccountCredentials(
            projectId = "test-proj",
            clientEmail = "sa@test.com",
            privateKeyPem = testPrivateKeyPem,
            defaultLocation = "global",
        )
        val credsUsCentral1 = credsGlobal.copy(defaultLocation = "us-central1")
        val credsEu = credsGlobal.copy(defaultLocation = "eu")

        // URL building with credentials
        assertEquals(
            "https://aiplatform.googleapis.com/v1/projects/test-proj/locations/global/publishers/google/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            VertexAiServiceAccountAuth.buildGenerateContentUrl("https://aiplatform.googleapis.com", "gemini-2.5-flash", stream = true, credentials = credsGlobal),
        )
        assertEquals(
            "https://us-central1-aiplatform.googleapis.com/v1/projects/test-proj/locations/us-central1/publishers/google/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            VertexAiServiceAccountAuth.buildGenerateContentUrl("https://aiplatform.googleapis.com", "gemini-2.5-flash", stream = true, credentials = credsUsCentral1),
        )
        assertEquals(
            "https://aiplatform.eu.rep.googleapis.com/v1/projects/test-proj/locations/eu/publishers/google/models/gemini-2.5-flash:generateContent",
            VertexAiServiceAccountAuth.buildGenerateContentUrl("https://aiplatform.googleapis.com", "gemini-2.5-flash", stream = false, credentials = credsEu),
        )

        // URL building with explicit baseUrl containing projects and locations
        assertEquals(
            "https://us-central1-aiplatform.googleapis.com/v1/projects/custom-proj/locations/us-central1/publishers/google/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            VertexAiServiceAccountAuth.buildGenerateContentUrl("https://us-central1-aiplatform.googleapis.com/v1/projects/custom-proj/locations/us-central1", "gemini-2.5-flash", stream = true, credentials = null),
        )

        // URL building for native Gemini with standard API Key
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            VertexAiServiceAccountAuth.buildGenerateContentUrl("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", stream = true, credentials = null),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            VertexAiServiceAccountAuth.buildGenerateContentUrl("https://generativelanguage.googleapis.com", "publishers/google/models/gemini-2.5-flash", stream = false, credentials = null),
        )
    }

    @Test
    fun testOAuth2TokenExchangeAndCachingWithHttpServer() {
        var tokenRequestCount = 0
        var receivedAuthHeader: String? = null
        var receivedGrantType: String? = null
        var receivedAssertion: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { exchange ->
            tokenRequestCount++
            receivedAuthHeader = exchange.requestHeaders.getFirst("Accept")
            val body = exchange.requestBody.bufferedReader().readText()
            val params = body.split("&").associate {
                val kv = it.split("=")
                java.net.URLDecoder.decode(kv[0], "UTF-8") to java.net.URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
            }
            receivedGrantType = params["grant_type"]
            receivedAssertion = params["assertion"]

            val responseJson = """{"access_token": "ya29.mock-cached-token-123", "expires_in": 3600, "token_type": "Bearer"}"""
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val port = server.address.port

        val okHttpClient = OkHttpClient()
        val credentials = VertexAiServiceAccountAuth.ServiceAccountCredentials(
            projectId = "test-project-123",
            clientEmail = "sa-test@test.iam.gserviceaccount.com",
            privateKeyPem = testPrivateKeyPem,
            tokenUri = "http://127.0.0.1:$port/token",
        )

        try {
            // First call: hits server
            val token1 = VertexAiServiceAccountAuth.getAccessToken(credentials, okHttpClient)
            assertEquals("ya29.mock-cached-token-123", token1)
            assertEquals(1, tokenRequestCount)
            assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", receivedGrantType)
            assertNotNull(receivedAssertion)

            // Second call: cached, should NOT hit server
            val token2 = VertexAiServiceAccountAuth.getAccessToken(credentials, okHttpClient)
            assertEquals("ya29.mock-cached-token-123", token2)
            assertEquals(1, tokenRequestCount)

            // Clear cache: third call hits server again
            VertexAiServiceAccountAuth.clearCacheForTests()
            val token3 = VertexAiServiceAccountAuth.getAccessToken(credentials, okHttpClient)
            assertEquals("ya29.mock-cached-token-123", token3)
            assertEquals(2, tokenRequestCount)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterPrepareAndThinkingConfig() {
        val adapter = GeminiAdapter()

        val messages = listOf(
            ProviderMessage(role = "system", content = "You are a creative writer."),
            ProviderMessage(role = "assistant", content = "Earlier story"),
            ProviderMessage(role = "user", content = "Continue the story"),
        )

        // Modern Gemini with thinking enabled
        val modernPrepared = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-4-argon",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(
                thinkingEnabled = true,
                reasoningStrength = com.ainovel.local.data.remote.ReasoningStrength.STRONG,
                maxOutputTokens = 4096,
            ),
        )
        val modernJson = json.parseToJsonElement(modernPrepared.serializedRequestBody).jsonObject
        val modernGenConfig = modernJson["generationConfig"]?.jsonObject
        val modernThinking = modernGenConfig?.get("thinkingConfig")?.jsonObject
        assertEquals(true, modernThinking?.get("includeThoughts")?.jsonPrimitive?.booleanOrNull)
        assertEquals("HIGH", modernThinking?.get("thinkingLevel")?.jsonPrimitive?.content)
        assertNull(modernThinking?.get("thinkingBudget"))

        // Modern Gemini with thinking disabled
        val modernDisabledPrepared = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-3.8-flash-tiered",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(
                thinkingEnabled = false,
                temperature = 0.5,
            ),
        )
        val modernDisabledJson = json.parseToJsonElement(modernDisabledPrepared.serializedRequestBody).jsonObject
        val modernDisabledThinking = modernDisabledJson["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject
        assertEquals(false, modernDisabledThinking?.get("includeThoughts")?.jsonPrimitive?.booleanOrNull)
        assertNull(modernDisabledThinking?.get("thinkingBudget"))
        assertNull(modernDisabledThinking?.get("thinkingLevel"))

        // Legacy Gemini (gemini-2.5-flash) with thinking enabled
        val legacyPrepared = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-2.5-flash",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(
                thinkingEnabled = true,
                reasoningStrength = com.ainovel.local.data.remote.ReasoningStrength.WEAK,
            ),
        )
        val legacyJson = json.parseToJsonElement(legacyPrepared.serializedRequestBody).jsonObject
        val legacyThinking = legacyJson["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject
        assertEquals(true, legacyThinking?.get("includeThoughts")?.jsonPrimitive?.booleanOrNull)
        assertEquals(1024, legacyThinking?.get("thinkingBudget")?.jsonPrimitive?.intOrNull)

        // gemini-2.5-flash with thinking disabled sets thinkingBudget = 0
        val legacyDisabledPrepared = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-2.5-flash",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(thinkingEnabled = false),
        )
        val legacyDisabledJson = json.parseToJsonElement(legacyDisabledPrepared.serializedRequestBody).jsonObject
        val legacyDisabledThinking = legacyDisabledJson["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject
        assertEquals(false, legacyDisabledThinking?.get("includeThoughts")?.jsonPrimitive?.booleanOrNull)
        assertEquals(0, legacyDisabledThinking?.get("thinkingBudget")?.jsonPrimitive?.intOrNull)

        // gemini-2.5-pro with thinking disabled does NOT set thinkingBudget = 0 (to avoid 400 error)
        val proDisabledPrepared = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-2.5-pro",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(thinkingEnabled = false),
        )
        val proDisabledJson = json.parseToJsonElement(proDisabledPrepared.serializedRequestBody).jsonObject
        val proDisabledThinking = proDisabledJson["generationConfig"]?.jsonObject?.get("thinkingConfig")?.jsonObject
        assertEquals(false, proDisabledThinking?.get("includeThoughts")?.jsonPrimitive?.booleanOrNull)
        assertNull(proDisabledThinking?.get("thinkingBudget"))

        // Verify prepended user turn when first message was assistant
        val contents = modernJson["contents"]?.jsonArray.orEmpty()
        assertTrue(contents.isNotEmpty())
        assertEquals("user", contents[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("model", contents[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", contents[2].jsonObject["role"]?.jsonPrimitive?.content)

        // Verify system instruction
        val sysInstruction = modernJson["systemInstruction"]?.jsonObject
        val sysText = sysInstruction?.get("parts")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
        assertEquals("You are a creative writer.", sysText)
    }

    @Test
    fun testGeminiAdapterParseModels() {
        val adapter = GeminiAdapter()

        val sampleModelsJson = """
            {
              "models": [
                {
                  "name": "models/gemini-2.5-pro",
                  "displayName": "Gemini 2.5 Pro",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-2.5-flash",
                  "displayName": "Gemini 2.5 Flash",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/text-embedding-004",
                  "displayName": "Text Embedding 004",
                  "supportedGenerationMethods": ["embedContent"]
                },
                {
                  "name": "models/imagen-3.0-generate-002",
                  "displayName": "Imagen 3",
                  "supportedGenerationMethods": ["generateImages"]
                }
              ]
            }
        """.trimIndent()

        val models = adapter.parseGeminiModels(sampleModelsJson)
        assertEquals(2, models.size)
        val pro = models.first { it.id == "gemini-2.5-pro" }
        assertEquals("Gemini 2.5 Pro", pro.displayName)
        assertTrue(pro.supportsReasoning)
        assertTrue(pro.supportsReasoningStrength)

        val flash = models.first { it.id == "gemini-2.5-flash" }
        assertEquals("Gemini 2.5 Flash", flash.displayName)
        assertTrue(flash.supportsReasoning)
        assertTrue(flash.supportsReasoningStrength)

        // Test publisherModels
        val samplePublisherJson = """
            {
              "publisherModels": [
                {
                  "name": "publishers/google/models/gemini-3.8-flash-tiered",
                  "displayName": "Gemini 3.8 Flash (Tiered)",
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
        """.trimIndent()
        val publisherModels = adapter.parseGeminiModels(samplePublisherJson)
        assertEquals(1, publisherModels.size)
        assertEquals("gemini-3.8-flash-tiered", publisherModels[0].id)
        assertTrue(publisherModels[0].supportsReasoning)
        assertTrue(publisherModels[0].supportsReasoningStrength)
    }

    @Test
    fun testGeminiAdapterStreamWithServiceAccountAndSse(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        var receivedAuthHeader: String? = null
        var receivedProjectHeader: String? = null

        server.createContext("/token") { exchange ->
            val responseJson = """{"access_token": "ya29.stream-test-token", "expires_in": 3600}"""
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.createContext("/v1/projects/test-project-123/locations/global/publishers/google/models/gemini-2.5-flash:streamGenerateContent") { exchange ->
            receivedAuthHeader = exchange.requestHeaders.getFirst("Authorization")
            receivedProjectHeader = exchange.requestHeaders.getFirst("x-goog-user-project")

            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)

            exchange.responseBody.use { out ->
                val chunk1 = """data: {"candidates":[{"content":{"parts":[{"text":"Thinking deeply...","thought":true}]}}]}""" + "\n\n"
                out.write(chunk1.toByteArray(Charsets.UTF_8))
                out.flush()

                val chunk2 = """data: {"candidates":[{"content":{"parts":[{"text":"Here is the story continuation."}]}}],"usageMetadata":{"promptTokenCount":25,"candidatesTokenCount":15,"totalTokenCount":40,"thoughtsTokenCount":8}}""" + "\n\n"
                out.write(chunk2.toByteArray(Charsets.UTF_8))
                out.flush()

                val chunk3 = """data: {"candidates":[{"finishReason":"STOP","content":{"parts":[]}}]}""" + "\n\n"
                out.write(chunk3.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
        server.start()

        try {
            val saJson = """
                {
                  "type": "service_account",
                  "project_id": "test-project-123",
                  "private_key": "${testPrivateKeyPem.replace("\n", "\\n")}",
                  "client_email": "sa-test@test-project-123.iam.gserviceaccount.com",
                  "token_uri": "http://127.0.0.1:$port/token",
                  "location": "global"
                }
            """.trimIndent()

            val adapter = GeminiAdapter()
            val prepared = adapter.prepare(
                taskType = TaskType.BODY,
                novelId = "n1",
                targetEntityId = "c1",
                modelId = "gemini-2.5-flash",
                logicalContext = emptyList(),
                messages = listOf(ProviderMessage(role = "user", content = "Tell a story")),
                settings = GenerationSettings(streamOutput = true, thinkingEnabled = true),
            )

            val events = adapter.stream(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = saJson,
                preparedRequest = prepared,
            ).toList()

            assertEquals("Bearer ya29.stream-test-token", receivedAuthHeader)
            assertEquals("test-project-123", receivedProjectHeader)

            assertTrue(events.isNotEmpty())
            val deltas = events.filterIsInstance<StreamEvent.Delta>()
            assertEquals(2, deltas.size)
            assertEquals("Thinking deeply...", deltas[0].reasoningContent)
            assertEquals("", deltas[0].content)
            assertNull(deltas[1].reasoningContent)
            assertEquals("Here is the story continuation.", deltas[1].content)

            val finished = events.filterIsInstance<StreamEvent.Finished>().first()
            assertEquals("Here is the story continuation.", finished.result.content)
            assertEquals("Thinking deeply...", finished.result.reasoningContent)
            assertEquals("STOP", finished.result.finishReason)
            assertTrue(finished.result.complete)
            assertNotNull(finished.result.usage)
            assertEquals(25L, finished.result.usage?.inputTokens)
            assertEquals(15L, finished.result.usage?.outputTokens)
            assertEquals(40L, finished.result.usage?.totalTokens)
            assertEquals(8L, finished.result.usage?.reasoningTokens)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterNonStreamingWithApiKey(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        var receivedApiKeyHeader: String? = null

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent") { exchange ->
            receivedApiKeyHeader = exchange.requestHeaders.getFirst("x-goog-api-key")

            val responseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Reasoning steps...", "thought": true },
                          { "text": "Non-streamed answer" }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 12,
                    "candidatesTokenCount": 6,
                    "totalTokenCount": 18,
                    "thoughtsTokenCount": 4
                  }
                }
            """.trimIndent()
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val adapter = GeminiAdapter()
            val prepared = adapter.prepare(
                taskType = TaskType.MODEL_VALIDATION,
                novelId = null,
                targetEntityId = null,
                modelId = "gemini-2.5-flash",
                logicalContext = emptyList(),
                messages = listOf(ProviderMessage(role = "user", content = "ping")),
                settings = GenerationSettings(streamOutput = false, thinkingEnabled = true),
            )

            val events = adapter.stream(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = "test-gemini-api-key-xyz",
                preparedRequest = prepared,
            ).toList()

            assertEquals("test-gemini-api-key-xyz", receivedApiKeyHeader)

            val finished = events.filterIsInstance<StreamEvent.Finished>().first()
            assertEquals("Non-streamed answer", finished.result.content)
            assertEquals("Reasoning steps...", finished.result.reasoningContent)
            assertEquals("STOP", finished.result.finishReason)
            assertEquals(12L, finished.result.usage?.inputTokens)
            assertEquals(6L, finished.result.usage?.outputTokens)
            assertEquals(18L, finished.result.usage?.totalTokens)
            assertEquals(4L, finished.result.usage?.reasoningTokens)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterErrorRedaction(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent") { exchange ->
            val errorJson = """
                {
                  "error": {
                    "message": "Invalid key secret-raw-key-12345 in $testPrivateKeyPem",
                    "code": 401
                  }
                }
            """.trimIndent()
            val bytes = errorJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val adapter = GeminiAdapter()
            val prepared = adapter.prepare(
                taskType = TaskType.MODEL_VALIDATION,
                novelId = null,
                targetEntityId = null,
                modelId = "gemini-2.5-flash",
                logicalContext = emptyList(),
                messages = listOf(ProviderMessage(role = "user", content = "ping")),
                settings = GenerationSettings(streamOutput = false),
            )

            try {
                adapter.stream(
                    baseUrl = "http://127.0.0.1:$port",
                    apiKey = "secret-raw-key-12345",
                    preparedRequest = prepared,
                ).toList()
                fail("Expected ProviderException on 401")
            } catch (e: ProviderException) {
                assertEquals(401, e.statusCode)
                assertFalse(e.responseBody.orEmpty().contains("secret-raw-key-12345"))
                assertFalse(e.responseBody.orEmpty().contains("MIIEvgIBADANBgkq"))
                assertTrue(e.responseBody.orEmpty().contains("***"))
                assertTrue(e.responseBody.orEmpty().contains("[REDACTED_PRIVATE_KEY]"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterListModelsWithServiceAccountAndCustomHost(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/token") { exchange ->
            val responseJson = """{"access_token": "ya29.list-models-token", "expires_in": 3600}"""
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.createContext("/v1beta/models") { exchange ->
            assertEquals("Bearer ya29.list-models-token", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("test-project-123", exchange.requestHeaders.getFirst("x-goog-user-project"))
            val responseJson = """
                {
                  "models": [
                    {
                      "name": "models/gemini-2.5-flash",
                      "displayName": "Gemini 2.5 Flash",
                      "supportedGenerationMethods": ["generateContent"]
                    }
                  ]
                }
            """.trimIndent()
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.createContext("/v1beta1/publishers/google/models") { exchange ->
            val responseJson = """
                {
                  "publisherModels": [
                    {
                      "name": "publishers/google/models/gemini-3.8-flash-tiered",
                      "displayName": "Gemini 3.8 Flash (Tiered)",
                      "supportedGenerationMethods": ["generateContent"]
                    }
                  ]
                }
            """.trimIndent()
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val saJson = """
                {
                  "type": "service_account",
                  "project_id": "test-project-123",
                  "private_key": "${testPrivateKeyPem.replace("\n", "\\n")}",
                  "client_email": "sa-test@test.iam.gserviceaccount.com",
                  "token_uri": "http://127.0.0.1:$port/token"
                }
            """.trimIndent()
            val adapter = GeminiAdapter()
            val models = adapter.listModels("http://127.0.0.1:$port", saJson)
            assertEquals(2, models.size)
            assertTrue(models.any { it.id == "gemini-2.5-flash" })
            assertTrue(models.any { it.id == "gemini-3.8-flash-tiered" })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterListModelsServiceAccountFallbackWhenEmpty(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/token") { exchange ->
            val responseJson = """{"access_token": "ya29.fallback-token", "expires_in": 3600}"""
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.createContext("/v1beta/models") { exchange ->
            exchange.sendResponseHeaders(403, 0)
            exchange.responseBody.close()
        }
        server.createContext("/v1beta1/publishers/google/models") { exchange ->
            exchange.sendResponseHeaders(403, 0)
            exchange.responseBody.close()
        }
        server.start()

        try {
            val saJson = """
                {
                  "type": "service_account",
                  "project_id": "test-project-123",
                  "private_key": "${testPrivateKeyPem.replace("\n", "\\n")}",
                  "client_email": "sa-test@test.iam.gserviceaccount.com",
                  "token_uri": "http://127.0.0.1:$port/token"
                }
            """.trimIndent()
            val adapter = GeminiAdapter()
            val models = adapter.listModels("http://127.0.0.1:$port", saJson)
            assertEquals(4, models.size)
            assertEquals("gemini-2.5-pro", models[0].id)
            assertEquals("gemini-2.5-flash", models[1].id)
            assertEquals("gemini-3.8-flash-tiered", models[2].id)
            assertEquals("gemini-4-argon", models[3].id)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterListModelsWithApiKey(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/v1beta/models") { exchange ->
            assertEquals("api-key-test-123", exchange.requestHeaders.getFirst("x-goog-api-key"))
            val responseJson = """
                {
                  "models": [
                    {
                      "name": "models/gemini-2.5-pro",
                      "displayName": "Gemini 2.5 Pro",
                      "supportedGenerationMethods": ["generateContent"]
                    }
                  ]
                }
            """.trimIndent()
            val bytes = responseJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val adapter = GeminiAdapter()
            val models = adapter.listModels("http://127.0.0.1:$port/v1beta/openai", "api-key-test-123")
            assertEquals(1, models.size)
            assertEquals("gemini-2.5-pro", models[0].id)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGeminiAdapterPromptBlockedBySafety(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent") { exchange ->
            val blockedJson = """
                {
                  "candidates": [],
                  "promptFeedback": {
                    "blockReason": "SAFETY"
                  }
                }
            """.trimIndent()
            val bytes = blockedJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val adapter = GeminiAdapter()
            val prepared = adapter.prepare(
                taskType = TaskType.MODEL_VALIDATION,
                novelId = null,
                targetEntityId = null,
                modelId = "gemini-2.5-flash",
                logicalContext = emptyList(),
                messages = listOf(ProviderMessage(role = "user", content = "unsafe content")),
                settings = GenerationSettings(streamOutput = false),
            )

            try {
                adapter.stream(
                    baseUrl = "http://127.0.0.1:$port",
                    apiKey = "api-key-xyz",
                    preparedRequest = prepared,
                ).toList()
                fail("Expected ProviderException when prompt is blocked")
            } catch (e: ProviderException) {
                assertEquals(400, e.statusCode)
                assertTrue(e.message.orEmpty().contains("SAFETY"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testProviderCatalogAndRepositoryManualCapabilities() {
        val geminiDef = ProviderCatalog.find("gemini")
        assertNotNull(geminiDef)
        assertEquals("Google Gemini", geminiDef?.displayName)
        assertEquals("https://generativelanguage.googleapis.com", geminiDef?.defaultBaseUrl)
        assertEquals("Gemini / Vertex AI 原生", geminiDef?.protocolLabel)

        val vertexDef = ProviderCatalog.find("vertex")
        assertNotNull(vertexDef)
        assertEquals("Google Cloud Vertex AI", vertexDef?.displayName)
        assertEquals("https://aiplatform.googleapis.com", vertexDef?.defaultBaseUrl)
        assertEquals("Vertex AI 原生", vertexDef?.protocolLabel)

        // Test manualModelCapabilities
        val flash15 = GenerationRepository.manualModelCapabilities("gemini", "gemini-1.5-flash")
        assertFalse(flash15.supportsReasoning)
        assertFalse(flash15.supportsReasoningStrength)

        val pro15 = GenerationRepository.manualModelCapabilities("gemini", "gemini-1.5-pro")
        assertFalse(pro15.supportsReasoning)
        assertFalse(pro15.supportsReasoningStrength)

        val flash25 = GenerationRepository.manualModelCapabilities("gemini", "gemini-2.5-flash")
        assertTrue(flash25.supportsReasoning)
        assertTrue(flash25.supportsReasoningStrength)

        val tiered38 = GenerationRepository.manualModelCapabilities("vertex", "publishers/google/models/gemini-3.8-flash-tiered")
        assertTrue(tiered38.supportsReasoning)
        assertTrue(tiered38.supportsReasoningStrength)

        val argon4 = GenerationRepository.manualModelCapabilities("vertex", "gemini-4-argon")
        assertTrue(argon4.supportsReasoning)
        assertTrue(argon4.supportsReasoningStrength)

        val imagen = GenerationRepository.manualModelCapabilities("gemini", "imagen-3.0-generate-002")
        assertFalse(imagen.supportsReasoning)
        assertFalse(imagen.supportsReasoningStrength)
    }

    @Test
    fun testGenerativeLanguageUrlWithServiceAccountRoutesToVertexAi() {
        val creds = VertexAiServiceAccountAuth.ServiceAccountCredentials(
            projectId = "my-test-proj",
            clientEmail = "sa@my-test-proj.iam.gserviceaccount.com",
            privateKeyPem = testPrivateKeyPem,
            defaultLocation = "global",
        )
        val url = VertexAiServiceAccountAuth.buildGenerateContentUrl(
            baseUrl = "https://generativelanguage.googleapis.com",
            model = "gemini-2.5-flash",
            stream = false,
            credentials = creds,
        )
        assertEquals(
            "https://aiplatform.googleapis.com/v1/projects/my-test-proj/locations/global/publishers/google/models/gemini-2.5-flash:generateContent",
            url,
        )

        val streamUrl = VertexAiServiceAccountAuth.buildGenerateContentUrl(
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "publishers/google/models/gemini-2.5-flash",
            stream = true,
            credentials = creds,
        )
        assertEquals(
            "https://aiplatform.googleapis.com/v1/projects/my-test-proj/locations/global/publishers/google/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            streamUrl,
        )
    }

    @Test
    fun testOAuth2TokenEndpointHttp400ThrowsProviderException401(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/token") { exchange ->
            val errorJson = """{"error": "invalid_grant", "error_description": "Invalid JWT Signature."}"""
            val bytes = errorJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        val okHttpClient = OkHttpClient()
        val credentials = VertexAiServiceAccountAuth.ServiceAccountCredentials(
            projectId = "test-project-123",
            clientEmail = "sa-test@test.iam.gserviceaccount.com",
            privateKeyPem = testPrivateKeyPem,
            tokenUri = "http://127.0.0.1:$port/token",
        )

        try {
            // Direct call to getAccessToken
            try {
                VertexAiServiceAccountAuth.getAccessToken(credentials, okHttpClient)
                fail("Expected ProviderException on HTTP 400 from token endpoint")
            } catch (e: ProviderException) {
                assertEquals(401, e.statusCode)
                assertFalse(e.retryable)
                assertTrue(e.message.orEmpty().contains("Google 服务账号认证失败（HTTP 400）"))
                assertTrue(e.message.orEmpty().contains("invalid_grant"))
            }

            // Call via GeminiAdapter.listModels
            val saJson = """
                {
                  "type": "service_account",
                  "project_id": "test-project-123",
                  "private_key": "${testPrivateKeyPem.replace("\n", "\\n")}",
                  "client_email": "sa-test@test.iam.gserviceaccount.com",
                  "token_uri": "http://127.0.0.1:$port/token"
                }
            """.trimIndent()
            val adapter = GeminiAdapter()
            try {
                adapter.listModels("http://127.0.0.1:$port", saJson)
                fail("Expected ProviderException on listModels when token endpoint returns 400")
            } catch (e: ProviderException) {
                assertEquals(401, e.statusCode)
                assertFalse(e.retryable)
            }

            // Call via GeminiAdapter.stream
            val prepared = adapter.prepare(
                taskType = TaskType.MODEL_VALIDATION,
                novelId = null,
                targetEntityId = null,
                modelId = "gemini-2.5-flash",
                logicalContext = emptyList(),
                messages = listOf(ProviderMessage(role = "user", content = "test")),
                settings = GenerationSettings(streamOutput = false),
            )
            try {
                adapter.stream("http://127.0.0.1:$port", saJson, prepared).toList()
                fail("Expected ProviderException on stream when token endpoint returns 400")
            } catch (e: ProviderException) {
                assertEquals(401, e.statusCode)
                assertFalse(e.retryable)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testGemini15FlashDoesNotIncludeThinkingConfigInPrepare() {
        val adapter = GeminiAdapter()
        val messages = listOf(ProviderMessage(role = "user", content = "Hello"))

        // gemini-1.5-flash with thinkingEnabled = false
        val preparedDisabled = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-1.5-flash",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(
                thinkingEnabled = false,
                temperature = 0.7,
                maxOutputTokens = 2048,
            ),
        )
        val jsonDisabled = json.parseToJsonElement(preparedDisabled.serializedRequestBody).jsonObject
        val genConfigDisabled = jsonDisabled["generationConfig"]?.jsonObject
        assertNotNull(genConfigDisabled)
        assertNull(genConfigDisabled?.get("thinkingConfig"))
        assertEquals(0.7, genConfigDisabled?.get("temperature")?.jsonPrimitive?.content?.toDouble() ?: 0.0, 0.001)
        assertEquals(2048, genConfigDisabled?.get("maxOutputTokens")?.jsonPrimitive?.intOrNull)

        // gemini-1.5-flash even with thinkingEnabled = true must NOT include thinkingConfig
        val preparedEnabled = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-1.5-flash",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(
                thinkingEnabled = true,
                temperature = 0.8,
            ),
        )
        val jsonEnabled = json.parseToJsonElement(preparedEnabled.serializedRequestBody).jsonObject
        val genConfigEnabled = jsonEnabled["generationConfig"]?.jsonObject
        assertNotNull(genConfigEnabled)
        assertNull(genConfigEnabled?.get("thinkingConfig"))
        assertEquals(0.8, genConfigEnabled?.get("temperature")?.jsonPrimitive?.content?.toDouble() ?: 0.0, 0.001)

        // gemini-1.5-pro must also NOT include thinkingConfig
        val proPrepared = adapter.prepare(
            taskType = TaskType.BODY,
            novelId = "n1",
            targetEntityId = "c1",
            modelId = "gemini-1.5-pro",
            logicalContext = emptyList(),
            messages = messages,
            settings = GenerationSettings(
                thinkingEnabled = false,
            ),
        )
        val proJson = json.parseToJsonElement(proPrepared.serializedRequestBody).jsonObject
        assertNull(proJson["generationConfig"]?.jsonObject?.get("thinkingConfig"))
    }

    @Test
    fun testVertexAiAndGeminiLocalization() {
        // ProviderCatalog labels and notes
        assertEquals("Google Cloud Vertex AI", localized("Google Cloud Vertex AI", AppLanguage.ENGLISH))
        assertEquals("Native Gemini / Vertex AI", localized("Gemini / Vertex AI 原生", AppLanguage.ENGLISH))
        assertEquals("Native Vertex AI", localized("Vertex AI 原生", AppLanguage.ENGLISH))
        assertEquals(
            "Supports Gemini API keys or directly pasting / importing Google Cloud Vertex AI service account JSON credentials.",
            localized("支持 Gemini API Key，或直接粘贴 / 导入 Google Cloud Vertex AI 服务账号 JSON 凭证。", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Import or paste a Google Cloud Service Account JSON key to automatically issue OAuth2 tokens and populate the project URL.",
            localized("导入或粘贴 Google Cloud 服务账号 (Service Account) JSON 密钥，自动签发 OAuth2 令牌并填充项目地址。", AppLanguage.ENGLISH)
        )

        // SettingsScreen action button and labels
        assertEquals("Import Service Account JSON", localized("导入服务账号 JSON", AppLanguage.ENGLISH))
        assertEquals("API Key or Service Account JSON", localized("API Key 或服务账号 JSON", AppLanguage.ENGLISH))
        assertEquals(
            "New API key or Service Account JSON (leave blank to keep the current key)",
            localized("新 API Key 或服务账号 JSON（留空表示不修改）", AppLanguage.ENGLISH)
        )
        assertEquals("Service Account JSON or API Key", localized("服务账号 JSON 或 API Key", AppLanguage.ENGLISH))
        assertEquals(
            "New Service Account JSON or API key (leave blank to keep the current key)",
            localized("新服务账号 JSON 或 API Key（留空表示不修改）", AppLanguage.ENGLISH)
        )

        // Dynamic service account loaded string
        assertEquals(
            "Loaded service account: sa-test@my-project.iam.gserviceaccount.com (project: my-project)",
            localized("已加载服务账号：sa-test@my-project.iam.gserviceaccount.com（项目：my-project）", AppLanguage.ENGLISH)
        )

        // Dynamic error message patterns
        assertEquals(
            "Invalid service account JSON format: missing brace",
            localized("服务账号 JSON 格式错误：missing brace", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Failed to parse service account credentials: invalid key",
            localized("服务账号凭证解析失败：invalid key", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Failed to parse or sign service account private key: bad key",
            localized("服务账号私钥解析或签名失败：bad key", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Failed to acquire Google OAuth2 access token: network error",
            localized("获取 Google OAuth2 访问令牌失败：network error", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Unable to connect to Google OAuth2 server: timeout",
            localized("无法连接 Google OAuth2 服务：timeout", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Unable to connect to Google OAuth2 server: timeout",
            localized("无法连接 Google OAuth2 服务器：timeout", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Google OAuth2 returned invalid JSON: eof",
            localized("Google OAuth2 返回的不是合法的 JSON：eof", AppLanguage.ENGLISH)
        )
        assertEquals(
            "Google service account authentication failed (HTTP 401): invalid_grant",
            localized("Google 服务账号认证失败（HTTP 401）：invalid_grant", AppLanguage.ENGLISH)
        )

        // Simplified Chinese returns original text unchanged
        assertEquals(
            "已加载服务账号：sa-test@my-project.iam.gserviceaccount.com（项目：my-project）",
            localized("已加载服务账号：sa-test@my-project.iam.gserviceaccount.com（项目：my-project）", AppLanguage.SIMPLIFIED_CHINESE)
        )
    }
}
