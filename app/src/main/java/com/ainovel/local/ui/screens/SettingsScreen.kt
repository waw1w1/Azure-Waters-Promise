package com.ainovel.local.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.ainovel.local.ui.localization.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.local.data.backup.BackupRestorePreview
import com.ainovel.local.data.model.ModelSettingEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.ProviderCatalog
import com.ainovel.local.data.remote.ProviderDefinition
import com.ainovel.local.data.remote.ReasoningStrength
import com.ainovel.local.data.remote.ProviderModel
import com.ainovel.local.data.remote.VertexAiServiceAccountAuth
import com.ainovel.local.domain.EpubExporter
import kotlinx.serialization.json.Json
import com.ainovel.local.ui.AppViewModel
import com.ainovel.local.ui.localization.AppLanguage
import com.ainovel.local.ui.components.PrimaryAction
import com.ainovel.local.ui.components.SecondaryAction
import com.ainovel.local.ui.components.SectionHeader
import com.ainovel.local.ui.components.StatusPill
import com.ainovel.local.ui.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ProviderModelJson = Json { ignoreUnknownKeys = true }

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.allModels.collectAsStateWithLifecycle()
    val bodySettings by viewModel.bodyGenerationSettings.collectAsStateWithLifecycle()
    val summarySettings by viewModel.summaryGenerationSettings.collectAsStateWithLifecycle()
    val assistantSettings by viewModel.assistantGenerationSettings.collectAsStateWithLifecycle()
    val rollbackAvailable by viewModel.rollbackBackupAvailable.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeader(
                "设置",
                description = "密钥由 Android Keystore 保护，普通数据库和请求历史中不保存明文。",
                trailing = {
                    val verifiedCount = providers.count { it.validationState.startsWith("VERIFIED") }
                    StatusPill(
                        when {
                            verifiedCount > 0 -> "$verifiedCount 个已连接"
                            providers.isNotEmpty() -> "待手动验证"
                            else -> "未连接"
                        },
                        if (verifiedCount > 0) StatusTone.SAVED else StatusTone.WARNING,
                    )
                },
            )
        }
        item { LanguageSection(language, onLanguageChange) }
        item { ProviderSection(viewModel, providers, busy) }
        if (workspace.novel != null) {
            item { TaskModelsSection(viewModel, models) }
            item {
                GenerationSettingsSection(
                    body = bodySettings,
                    summary = summarySettings,
                    assistant = assistantSettings,
                    bodyCapabilities = models.firstOrNull { it.providerId == workspace.novel?.bodyProviderId && it.modelId == workspace.novel?.bodyModelId }?.providerModel(),
                    summaryCapabilities = models.firstOrNull { it.providerId == (workspace.novel?.summaryProviderId ?: workspace.novel?.bodyProviderId) && it.modelId == (workspace.novel?.summaryModelId ?: workspace.novel?.bodyModelId) }?.providerModel(),
                    assistantCapabilities = models.firstOrNull { it.providerId == (workspace.novel?.assistantProviderId ?: workspace.novel?.bodyProviderId) && it.modelId == (workspace.novel?.assistantModelId ?: workspace.novel?.bodyModelId) }?.providerModel(),
                    onChange = viewModel::updateGenerationSettings,
                )
            }
            item { InstructionSection(viewModel) }
        }
        item { BackupSection(viewModel, workspace.novel?.name, rollbackAvailable, busy) }
    }
}

@Composable
private fun LanguageSection(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                "语言",
                description = "切换后立即应用，并会记住你的选择。",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = language == AppLanguage.SIMPLIFIED_CHINESE,
                    onClick = { onLanguageChange(AppLanguage.SIMPLIFIED_CHINESE) },
                    label = {
                        androidx.compose.material3.Text(
                            if (language == AppLanguage.SIMPLIFIED_CHINESE) "简体中文" else "Simplified Chinese",
                        )
                    },
                )
                FilterChip(
                    selected = language == AppLanguage.ENGLISH,
                    onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                    label = { androidx.compose.material3.Text("English") },
                )
            }
        }
    }
}

@Composable
private fun ProviderSection(viewModel: AppViewModel, providers: List<ProviderConfigEntity>, busy: Boolean) {
    val context = LocalContext.current
    var selectedProviderId by remember { mutableStateOf("deepseek") }
    val definition = ProviderCatalog.find(selectedProviderId) ?: ProviderCatalog.definitions.first()
    val provider = providers.firstOrNull { it.providerId == selectedProviderId }
    val configured = provider != null
    var baseUrl by remember(selectedProviderId, provider?.baseUrl) { mutableStateOf(provider?.baseUrl ?: definition.defaultBaseUrl) }
    var apiKey by remember(selectedProviderId) { mutableStateOf("") }
    var revealApiKey by remember { mutableStateOf(false) }
    var importMessage by remember(selectedProviderId) { mutableStateOf<String?>(null) }
    var importIsError by remember(selectedProviderId) { mutableStateOf(false) }

    val isGeminiOrVertex = selectedProviderId == "gemini" || selectedProviderId == "vertex"

    val serviceAccountImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val rawJson = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("无法读取选定文件")
                val cleanJson = rawJson.trim().removePrefix("\uFEFF").trim()
                val parsed = VertexAiServiceAccountAuth.parseServiceAccountJson(cleanJson)
                apiKey = cleanJson
                val trimmedBaseUrl = baseUrl.trim()
                val shouldUpdateBaseUrl = trimmedBaseUrl.isBlank() ||
                    trimmedBaseUrl == "https://generativelanguage.googleapis.com" ||
                    trimmedBaseUrl == "https://generativelanguage.googleapis.com/v1beta/openai" ||
                    trimmedBaseUrl == "https://aiplatform.googleapis.com" ||
                    trimmedBaseUrl.contains("aiplatform.googleapis.com")
                if (shouldUpdateBaseUrl) {
                    val currentLocation = if (trimmedBaseUrl.contains("aiplatform.googleapis.com")) {
                        VertexAiServiceAccountAuth.resolveLocation(trimmedBaseUrl, parsed.defaultLocation)
                    } else {
                        parsed.defaultLocation
                    }
                    baseUrl = VertexAiServiceAccountAuth.buildDefaultVertexBaseUrl(parsed.projectId, currentLocation)
                }
                importMessage = null
                importIsError = false
            }.onFailure { e ->
                importMessage = e.message ?: "导入服务账号 JSON 失败"
                importIsError = true
            }
        }
    }

    val parsedServiceAccount = remember(apiKey) {
        if (VertexAiServiceAccountAuth.looksLikeServiceAccountJson(apiKey)) {
            runCatching { VertexAiServiceAccountAuth.parseServiceAccountJson(apiKey) }.getOrNull()
        } else {
            null
        }
    }

    val apiKeyLabel = when (selectedProviderId) {
        "vertex" -> if (configured) "新服务账号 JSON 或 API Key（留空表示不修改）" else "服务账号 JSON 或 API Key"
        "gemini" -> if (configured) "新 API Key 或服务账号 JSON（留空表示不修改）" else "API Key 或服务账号 JSON"
        else -> if (configured) "新 API Key（留空表示不修改）" else "API Key"
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("模型供应商", description = "选择供应商后配置 API；保存前会调用模型列表接口真实验证。")
            ProviderDropdown(
                selected = definition,
                configuredIds = providers.mapTo(mutableSetOf()) { it.providerId },
                onSelect = {
                    selectedProviderId = it.id
                    revealApiKey = false
                    apiKey = ""
                    importMessage = null
                    importIsError = false
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    when {
                        provider?.validationState?.startsWith("VERIFIED") == true -> "API 已验证"
                        provider != null -> "API 已保存，待验证模型"
                        else -> "API 未配置"
                    },
                    if (provider?.validationState?.startsWith("VERIFIED") == true) StatusTone.SAVED else StatusTone.WARNING,
                )
                Text(definition.protocolLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            definition.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (provider?.validationState == "MODEL_LIST_UNAVAILABLE") {
                Text(
                    "模型列表暂不可用，连接信息已加密保存。请在“本作任务模型”中填写手动模型 ID，并完成低成本真实生成验证。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (isGeminiOrVertex) {
                SecondaryAction(
                    "导入服务账号 JSON",
                    onClick = { serviceAccountImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.UploadFile,
                )
            }
            if (parsedServiceAccount != null) {
                Text(
                    "已加载服务账号：${parsedServiceAccount.clientEmail}（项目：${parsedServiceAccount.projectId}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            importMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (importIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    importMessage = null
                    importIsError = false
                },
                label = { Text(apiKeyLabel) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = !isGeminiOrVertex,
                maxLines = if (isGeminiOrVertex) 4 else 1,
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (!revealApiKey && apiKey.isBlank() && configured) {
                            viewModel.revealSavedApiKey(selectedProviderId) { saved -> apiKey = saved.orEmpty() }
                        }
                        revealApiKey = !revealApiKey
                    }) {
                        Icon(
                            if (revealApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (revealApiKey) "隐藏 API Key" else "显示 API Key",
                        )
                    }
                },
            )
            if (configured) {
                AdaptiveActionPair(
                    primary = { modifier ->
                        PrimaryAction(
                            text = if (busy) "验证中…" else "验证并安全保存",
                            onClick = { viewModel.saveProvider(selectedProviderId, apiKey, baseUrl, null) { apiKey = ""; revealApiKey = false; importMessage = null; importIsError = false } },
                            modifier = modifier,
                            enabled = !busy,
                            icon = Icons.Outlined.Security,
                        )
                    },
                    secondary = { modifier ->
                        SecondaryAction(
                            "刷新模型",
                            onClick = { viewModel.refreshModels(selectedProviderId) },
                            modifier = modifier,
                            enabled = !busy,
                            icon = Icons.Outlined.CloudSync,
                        )
                    },
                )
            } else {
                PrimaryAction(
                    text = if (busy) "验证中…" else "验证并安全保存",
                    onClick = { viewModel.saveProvider(selectedProviderId, apiKey, baseUrl, null) { apiKey = ""; revealApiKey = false; importMessage = null; importIsError = false } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiKey.isNotBlank() && !busy,
                    icon = Icons.Outlined.Security,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selected: ProviderDefinition,
    configuredIds: Set<String>,
    onSelect: (ProviderDefinition) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("供应商") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProviderCatalog.definitions.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName + if (item.id in configuredIds) " · 已配置" else "") },
                    onClick = { onSelect(item); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TaskModelsSection(viewModel: AppViewModel, models: List<ModelSettingEntity>) {
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    var manualModel by remember { mutableStateOf("") }
    var manualProviderId by remember(providers) {
        mutableStateOf(providers.firstOrNull { it.validationState.startsWith("VERIFIED") }?.providerId ?: providers.firstOrNull()?.providerId.orEmpty())
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader("本作任务模型", description = "正文、总结和助手可分别选择。手动 ID 会用低成本真实请求验证。")
            if (models.isEmpty()) {
                Text("先验证厂商连接，或在下方填写手动模型 ID。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ModelDropdown("正文模型", workspace.novel?.bodyProviderId, workspace.novel?.bodyModelId, models) { viewModel.selectTaskModel(TaskType.BODY, it.providerId, it.modelId) }
                ModelDropdown("总结模型", workspace.novel?.summaryProviderId ?: workspace.novel?.bodyProviderId, workspace.novel?.summaryModelId ?: workspace.novel?.bodyModelId, models) { viewModel.selectTaskModel(TaskType.SUMMARY, it.providerId, it.modelId) }
                ModelDropdown("助手模型", workspace.novel?.assistantProviderId ?: workspace.novel?.bodyProviderId, workspace.novel?.assistantModelId ?: workspace.novel?.bodyModelId, models) { viewModel.selectTaskModel(TaskType.ASSISTANT, it.providerId, it.modelId) }
            }
            if (providers.isNotEmpty()) {
                ConfiguredProviderDropdown(
                    selectedProviderId = manualProviderId,
                    providers = providers,
                    onSelect = { manualProviderId = it },
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 520.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = manualModel, onValueChange = { manualModel = it }, label = { Text("手动模型 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        PrimaryAction(
                            "验证并添加",
                            onClick = { viewModel.addManualModel(manualProviderId, manualModel) { manualModel = "" } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualProviderId.isNotBlank() && manualModel.isNotBlank(),
                            icon = Icons.Outlined.Add,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = manualModel, onValueChange = { manualModel = it }, label = { Text("手动模型 ID") }, modifier = Modifier.weight(1f), singleLine = true)
                        PrimaryAction(
                            "验证并添加",
                            onClick = { viewModel.addManualModel(manualProviderId, manualModel) { manualModel = "" } },
                            enabled = manualProviderId.isNotBlank() && manualModel.isNotBlank(),
                            icon = Icons.Outlined.Add,
                        )
                    }
                }
            }
            models.forEach { model ->
                MaxContextRow(model, onSave = { viewModel.setMaxContext(model.providerId, model.modelId, it) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    label: String,
    selectedProviderId: String?,
    selectedModelId: String?,
    models: List<ModelSettingEntity>,
    onSelect: (ModelSettingEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = models.firstOrNull { it.providerId == selectedProviderId && it.modelId == selectedModelId }
                ?.let { "${ProviderCatalog.find(it.providerId)?.displayName ?: it.providerId} · ${it.modelId}" }
                ?: "请选择",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text("${ProviderCatalog.find(model.providerId)?.displayName ?: model.providerId} · ${model.modelId}") },
                    onClick = { onSelect(model); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfiguredProviderDropdown(
    selectedProviderId: String,
    providers: List<ProviderConfigEntity>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = providers.firstOrNull { it.providerId == selectedProviderId }?.displayName ?: "选择已配置供应商"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("手动模型所属供应商") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = { onSelect(provider.providerId); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun MaxContextRow(model: ModelSettingEntity, onSave: (String) -> Unit) {
    var value by remember(model.modelId, model.maxContextTokens) { mutableStateOf(model.maxContextTokens.toString()) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val details: @Composable () -> Unit = {
            Column {
                Text("${ProviderCatalog.find(model.providerId)?.displayName ?: model.providerId} · ${model.modelId}", style = MaterialTheme.typography.labelLarge)
                Text(if (model.userModified) "本地手动上限" else "本地默认上限", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val editor: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                label = { Text("最大上下文 Token") },
                modifier = modifier,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editor(Modifier.weight(1f))
                    TextButton(onClick = { onSave(value) }, enabled = value.toLongOrNull()?.let { it > 0 } == true) { Text("保存") }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { details() }
                editor(Modifier.weight(1f))
                TextButton(onClick = { onSave(value) }, enabled = value.toLongOrNull()?.let { it > 0 } == true) { Text("保存") }
            }
        }
    }
}

@Composable
private fun GenerationSettingsSection(
    body: GenerationSettings,
    summary: GenerationSettings,
    assistant: GenerationSettings,
    bodyCapabilities: ProviderModel?,
    summaryCapabilities: ProviderModel?,
    assistantCapabilities: ProviderModel?,
    onChange: (TaskType, GenerationSettings) -> Unit,
) {
    var task by remember { mutableStateOf(TaskType.BODY) }
    val current = when (task) { TaskType.BODY -> body; TaskType.SUMMARY -> summary; else -> assistant }
    val capabilities = when (task) { TaskType.BODY -> bodyCapabilities; TaskType.SUMMARY -> summaryCapabilities; else -> assistantCapabilities }
    val supportsThinking = capabilities?.supportsReasoning == true
    val supportsStrength = capabilities?.supportsReasoningStrength == true
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("生成参数", description = "数值会进入请求检查和不可变快照。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskType.BODY to "正文", TaskType.SUMMARY to "总结", TaskType.ASSISTANT to "助手").forEach { (value, label) ->
                    FilterChip(selected = task == value, onClick = { task = value }, label = { Text(label) })
                }
            }
            ToggleRow("流式输出", "边接收边持久化；中断时保留不完整内容。", current.streamOutput) {
                onChange(task, current.copy(streamOutput = it))
            }
            ToggleRow("开启思考", if (supportsThinking) "推理内容与最终文本分开保存，不进入正文。" else "当前模型未声明支持思考内容。", current.thinkingEnabled, enabled = supportsThinking) {
                onChange(task, current.copy(thinkingEnabled = it))
            }
            ToggleRow("流式显示思考", "只在开启思考时有效。", current.streamThinking, enabled = current.thinkingEnabled) {
                onChange(task, current.copy(streamThinking = it))
            }
            if (current.thinkingEnabled && supportsStrength) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("推理强度", modifier = Modifier.weight(1f))
                    listOf(ReasoningStrength.WEAK, ReasoningStrength.MEDIUM, ReasoningStrength.STRONG).forEach { strength ->
                        FilterChip(
                            selected = current.reasoningStrength == strength,
                            onClick = { onChange(task, current.copy(reasoningStrength = strength)) },
                            label = { Text(when (strength) { ReasoningStrength.WEAK -> "弱"; ReasoningStrength.MEDIUM -> "中"; ReasoningStrength.STRONG -> "强"; ReasoningStrength.UNSUPPORTED -> "不支持" }) },
                        )
                    }
                }
            }
            if (current.thinkingEnabled && !supportsStrength) {
                Text("当前模型只支持思考开关，强度标记为 unsupported，不会发送 reasoning_effort。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!current.thinkingEnabled) {
                val temperature = current.temperature ?: 0.7
                Text("温度 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.labelLarge)
                Slider(value = temperature.toFloat(), onValueChange = { onChange(task, current.copy(temperature = (it * 10).toInt() / 10.0)) }, valueRange = 0f..2f, steps = 19)
            } else {
                Text("思考模式不使用温度参数。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ModelSettingEntity.providerModel(): ProviderModel? =
    runCatching { ProviderModelJson.decodeFromString<ProviderModel>(capabilitiesJson) }.getOrNull()

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun InstructionSection(viewModel: AppViewModel) {
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val novel = workspace.novel ?: return
    var summary by remember(novel.id, novel.summaryInstruction) { mutableStateOf(novel.summaryInstruction) }
    var assistant by remember(novel.id, novel.assistantInstruction) { mutableStateOf(novel.assistantInstruction) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("本作独立指令", description = "本作保存后独立维护；应用默认值只影响以后新建的小说。")
            OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("总结指令") }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 6)
            OutlinedTextField(value = assistant, onValueChange = { assistant = it }, label = { Text("助手指令") }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp), minLines = 5)
            AdaptiveActionPair(
                primary = { modifier -> PrimaryAction("保存本作指令", onClick = { viewModel.updateNovelInstructions(summary, assistant) }, modifier = modifier, enabled = summary.isNotBlank() && assistant.isNotBlank()) },
                secondary = { modifier -> SecondaryAction("重置本作总结", onClick = viewModel::resetNovelSummaryToAppDefault, modifier = modifier) },
            )
            HorizontalDivider()
            Text("应用默认总结指令", style = MaterialTheme.typography.titleMedium)
            Text("可将当前本作总结指令保存给以后新建小说；恢复出厂不会改动任何已有小说。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AdaptiveActionPair(
                primary = { modifier -> SecondaryAction("保存为应用默认", onClick = viewModel::saveSummaryAsAppDefault, modifier = modifier) },
                secondary = { modifier -> TextButton(onClick = viewModel::resetAppSummaryDefaultToFactory, modifier = modifier) { Text("应用默认恢复出厂") } },
            )
        }
    }
}

@Composable
private fun BackupSection(
    viewModel: AppViewModel,
    novelName: String?,
    rollbackAvailable: Boolean,
    busy: Boolean,
) {
    var createPassword by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupRestorePreview?>(null) }
    var rollbackPassword by remember { mutableStateOf<String?>(null) }
    var pendingCreatePassword by remember { mutableStateOf<String?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingCreatePassword
        if (uri != null && password != null) viewModel.createBackup(uri, password.toCharArray())
        pendingCreatePassword = null
    }
    val epubLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { uri ->
        if (uri != null) viewModel.exportNovelEpub(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; restorePassword = ""; preview = null }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("完整加密备份", description = "单个 .ainovelbackup 包含全部持久化业务数据和加密密钥区。备份密码不上传、不代管。")
            AdaptiveActionPair(
                primary = { modifier -> PrimaryAction("创建备份", onClick = { createPassword = "" }, modifier = modifier, enabled = !busy, icon = Icons.Outlined.Backup) },
                secondary = { modifier -> SecondaryAction("选择备份恢复", onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }, modifier = modifier, enabled = !busy, icon = Icons.Outlined.Restore) },
            )
            if (novelName != null) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("导出本作 EPUB", style = MaterialTheme.typography.titleMedium)
                        Text("将当前作品的正文导出为标准 EPUB 电子书，方便在各类阅读器中阅读。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SecondaryAction(
                        "导出本作 EPUB",
                        onClick = { epubLauncher.launch(EpubExporter.buildSuggestedFileName(novelName)) },
                        enabled = !busy,
                        icon = Icons.Outlined.Book,
                    )
                }
            }
            if (rollbackAvailable) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("有恢复前回滚备份", style = MaterialTheme.typography.titleMedium)
                        Text("使用上次恢复时的备份密码可回到覆盖前状态。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SecondaryAction("回滚", onClick = { rollbackPassword = "" }, enabled = !busy)
                }
            }
        }
    }

    createPassword?.let { password ->
        var value by remember { mutableStateOf(password) }
        var repeat by remember { mutableStateOf("") }
        PasswordDialog(
            title = "创建完整备份",
            description = "至少 8 个字符。忘记密码将无法恢复备份。",
            value = value,
            onValueChange = { value = it },
            repeatValue = repeat,
            onRepeatChange = { repeat = it },
            confirmText = "选择保存位置",
            confirmEnabled = value.length >= 8 && value == repeat,
            onDismiss = { createPassword = null },
            onConfirm = {
                pendingCreatePassword = value
                createPassword = null
                createLauncher.launch("local-novel-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.ainovelbackup")
            },
        )
    }
    restorePassword?.let { initial ->
        var value by remember { mutableStateOf(initial) }
        PasswordDialog(
            title = "先解密并预览",
            description = "此时不会修改当前数据。校验通过后再确认覆盖。",
            value = value,
            onValueChange = { value = it },
            confirmText = "解密预览",
            confirmEnabled = value.length >= 8,
            onDismiss = { restorePassword = null; restoreUri = null },
            onConfirm = {
                val uri = restoreUri ?: return@PasswordDialog
                viewModel.previewBackup(uri, value.toCharArray()) { result -> preview = result; restorePassword = value }
            },
        )
    }
    if (preview != null && restoreUri != null && restorePassword != null) {
        val result = requireNotNull(preview)
        AlertDialog(
            onDismissRequest = { preview = null; restorePassword = null; restoreUri = null },
            title = { Text("确认完整覆盖？") },
            text = {
                Text("备份包含 ${result.novelCount} 部小说、${result.chapterCount} 个章节、${result.summaryCount} 个总结、${result.candidateCount} 个候选、${result.requestCount} 条请求。\n\n恢复不会静默合并，将完整替换当前数据；软件会先自动保留当前状态供回滚。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreBackup(requireNotNull(restoreUri), requireNotNull(restorePassword).toCharArray())
                    preview = null; restorePassword = null; restoreUri = null
                }) { Text("创建回滚备份并覆盖") }
            },
            dismissButton = { TextButton(onClick = { preview = null; restorePassword = null; restoreUri = null }) { Text("取消") } },
        )
    }
    rollbackPassword?.let { initial ->
        var value by remember { mutableStateOf(initial) }
        PasswordDialog(
            title = "恢复覆盖前状态",
            description = "请输入上次恢复时使用的备份密码。",
            value = value,
            onValueChange = { value = it },
            confirmText = "回滚",
            confirmEnabled = value.length >= 8,
            onDismiss = { rollbackPassword = null },
            onConfirm = { viewModel.restoreRollbackBackup(value.toCharArray()); rollbackPassword = null },
        )
    }
}

@Composable
private fun AdaptiveActionPair(
    primary: @Composable (Modifier) -> Unit,
    secondary: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 440.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                primary(Modifier)
                secondary(Modifier)
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    repeatValue: String? = null,
    onRepeatChange: ((String) -> Unit)? = null,
    confirmText: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var revealPassword by remember { mutableStateOf(false) }
    var revealRepeat by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("备份密码") },
                    visualTransformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealPassword = !revealPassword }) {
                            Icon(
                                if (revealPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (revealPassword) "隐藏备份密码" else "显示备份密码",
                            )
                        }
                    },
                    singleLine = true,
                )
                if (repeatValue != null && onRepeatChange != null) {
                    OutlinedTextField(
                        value = repeatValue,
                        onValueChange = onRepeatChange,
                        label = { Text("再次输入") },
                        visualTransformation = if (revealRepeat) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { revealRepeat = !revealRepeat }) {
                                Icon(
                                    if (revealRepeat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (revealRepeat) "隐藏确认密码" else "显示确认密码",
                                )
                            }
                        },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = confirmEnabled, onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
