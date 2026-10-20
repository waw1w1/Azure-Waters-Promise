package com.ainovel.local.data.remote

data class ProviderDefinition(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val protocolLabel: String = "OpenAI 兼容",
    val note: String? = null,
)

object ProviderCatalog {
    val definitions = listOf(
        ProviderDefinition("deepseek", "DeepSeek", "https://api.deepseek.com"),
        ProviderDefinition("openai", "OpenAI", "https://api.openai.com/v1"),
        ProviderDefinition(
            "anthropic",
            "Anthropic Claude",
            "https://api.anthropic.com/v1",
            protocolLabel = "Anthropic 原生",
            note = "使用官方 /models 与 /messages 接口；高级思考参数暂不主动发送。",
        ),
        ProviderDefinition(
            id = "gemini",
            displayName = "Google Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com",
            protocolLabel = "Gemini / Vertex AI 原生",
            note = "支持 Gemini API Key，或直接粘贴 / 导入 Google Cloud Vertex AI 服务账号 JSON 凭证。",
        ),
        ProviderDefinition(
            id = "vertex",
            displayName = "Google Cloud Vertex AI",
            defaultBaseUrl = "https://aiplatform.googleapis.com",
            protocolLabel = "Vertex AI 原生",
            note = "导入或粘贴 Google Cloud 服务账号 (Service Account) JSON 密钥，自动签发 OAuth2 令牌并填充项目地址。",
        ),
        ProviderDefinition("moonshot", "Kimi / Moonshot", "https://api.moonshot.cn/v1"),
        ProviderDefinition("bailian", "阿里云百炼 / 通义", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        ProviderDefinition("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
        ProviderDefinition("minimax", "MiniMax", "https://api.minimaxi.com/v1"),
        ProviderDefinition("xai", "xAI", "https://api.x.ai/v1"),
        ProviderDefinition("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
        ProviderDefinition("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1"),
        ProviderDefinition(
            "openai-compatible",
            "通用 OpenAI 兼容接口",
            "https://",
            note = "填写服务商给出的完整 Base URL；若不能获取模型列表，可手动验证模型 ID。",
        ),
        ProviderDefinition(
            "anthropic-compatible",
            "通用 Anthropic 兼容接口",
            "https://",
            protocolLabel = "Anthropic 兼容",
            note = "填写包含 /v1 的 Base URL；支持 /models 与 /messages。",
        ),
    )

    fun find(id: String): ProviderDefinition? = definitions.firstOrNull { it.id == id }
}
