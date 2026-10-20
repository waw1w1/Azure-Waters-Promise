package com.ainovel.local.ui

import android.app.Application
import android.net.Uri
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.local.LocalNovelApplication
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.NovelEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.model.UsageRecordEntity
import com.ainovel.local.data.model.AcceptanceRecordEntity
import com.ainovel.local.data.model.ModelSettingEntity
import com.ainovel.local.data.model.AppSettingsEntity
import com.ainovel.local.data.model.SummaryDensity
import com.ainovel.local.data.model.ContextNodeType
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.ReasoningStrength
import com.ainovel.local.data.remote.PreparedRequest
import com.ainovel.local.data.remote.ProviderCatalog
import com.ainovel.local.data.remote.ProviderMessage
import com.ainovel.local.domain.GenerationProgress
import com.ainovel.local.domain.HistoryCleanupPreview
import com.ainovel.local.domain.SummarySourceSelection
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import com.ainovel.local.data.backup.BackupRestorePreview
import com.ainovel.local.domain.EpubExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AppDestination { WORKSPACE, CONTEXT, ASSISTANT, HISTORY, SETTINGS }

data class WorkspaceUiState(
    val novel: NovelEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val selectedChapter: ChapterEntity? = null,
    val summaries: List<SummaryEntity> = emptyList(),
    val activeGroups: List<CandidateGroupEntity> = emptyList(),
    val candidates: List<CandidateEntity> = emptyList(),
    val latestUsage: UsageRecordEntity? = null,
)

data class AssistantLiveUiState(
    val active: Boolean = false,
    val content: String = "",
    val reasoning: String = "",
)

sealed interface UiMessage {
    data class Info(val text: String) : UiMessage
    data class Error(val text: String) : UiMessage
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LocalNovelApplication).container
    private val novelRepository = container.novelRepository
    private val generationRepository = container.generationRepository
    private val contextAssembler = container.contextAssembler

    val destination = MutableStateFlow(AppDestination.WORKSPACE)
    private var destinationBeforeSettings = AppDestination.WORKSPACE
    val selectedNovelId = MutableStateFlow<String?>(null)
    val selectedChapterId = MutableStateFlow<String?>(null)
    val creativeRequirement = MutableStateFlow("")
    val bodyGenerationSettings = MutableStateFlow(GenerationSettings())
    val summaryGenerationSettings = MutableStateFlow(
        GenerationSettings(streamOutput = true, thinkingEnabled = false),
    )
    val assistantGenerationSettings = MutableStateFlow(
        GenerationSettings(streamOutput = true, thinkingEnabled = false),
    )
    val isBusy = MutableStateFlow(false)
    val rollbackBackupAvailable = MutableStateFlow(container.backupRepository.rollbackFile().exists())
    val preparedRequest = MutableStateFlow<PreparedRequest?>(null)
    val candidatePanelExpanded = MutableStateFlow(true)
    val assistantLive = MutableStateFlow(AssistantLiveUiState())
    val summarySourceSelections = MutableStateFlow<List<SummarySourceSelection>>(emptyList())
    val summaryDensity = MutableStateFlow(SummaryDensity.STANDARD)
    private val pendingSummarySelections = MutableStateFlow<List<SummarySourceSelection>>(emptyList())
    private val pendingBodyRequirement = MutableStateFlow<String?>(null)
    private val pendingAssistantMessage = MutableStateFlow<String?>(null)
    private val pendingAssistantPreviousMessages = MutableStateFlow<List<ProviderMessage>>(emptyList())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    val novels: StateFlow<List<NovelEntity>> = novelRepository.observeNovels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val novel = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(null) else novelRepository.observeNovel(id)
    }
    private val chapters = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeChapters(id)
    }
    private val summaries = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeSummaries(id)
    }
    val allChapters: StateFlow<List<ChapterEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeAllChapters(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appSettings: StateFlow<AppSettingsEntity?> = novelRepository.observeAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val groups = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeActiveGroups(id)
    }
    private val candidates = groups.flatMapLatest { active ->
        active.firstOrNull()?.let { novelRepository.observeCandidates(it.id) } ?: flowOf(emptyList())
    }
    private val bodyUsage = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(null) else novelRepository.observeLatestUsage(id, TaskType.BODY)
    }

    val workspace: StateFlow<WorkspaceUiState> = combine(
        novel,
        chapters,
        selectedChapterId,
        summaries,
        groups,
        candidates,
        bodyUsage,
    ) { values ->
        val novelValue = values[0] as NovelEntity?
        @Suppress("UNCHECKED_CAST") val chapterList = values[1] as List<ChapterEntity>
        val selectedId = values[2] as String?
        @Suppress("UNCHECKED_CAST") val summaryList = values[3] as List<SummaryEntity>
        @Suppress("UNCHECKED_CAST") val groupList = values[4] as List<CandidateGroupEntity>
        @Suppress("UNCHECKED_CAST") val candidateList = values[5] as List<CandidateEntity>
        val usage = values[6] as UsageRecordEntity?
        val selected = chapterList.firstOrNull { it.id == selectedId } ?: chapterList.firstOrNull()
        if (selected != null && selectedId != selected.id) selectedChapterId.value = selected.id
        WorkspaceUiState(
            novelValue,
            chapterList,
            selected,
            summaryList,
            groupList,
            resolveActiveCandidates(groupList, candidateList),
            usage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkspaceUiState())

    val providers: StateFlow<List<ProviderConfigEntity>> = novelRepository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allModels = novelRepository.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<RequestSnapshotEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeHistory(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyCandidates: StateFlow<List<CandidateEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeHistoryCandidates(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyUsage: StateFlow<List<UsageRecordEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeHistoryUsage(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val acceptances: StateFlow<List<AcceptanceRecordEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else novelRepository.observeAcceptances(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val presets: StateFlow<List<ContextEntryEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else novelRepository.observeContextEntries(EntryScope.NOVEL, EntryType.PRESET, id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val worldbook: StateFlow<List<ContextEntryEntity>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else novelRepository.observeContextEntries(EntryScope.NOVEL, EntryType.WORLDBOOK, id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val globalPresets: StateFlow<List<ContextEntryEntity>> = novelRepository
        .observeContextEntries(EntryScope.GLOBAL, EntryType.PRESET, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val globalWorldbook: StateFlow<List<ContextEntryEntity>> = novelRepository
        .observeContextEntries(EntryScope.GLOBAL, EntryType.WORLDBOOK, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assistantMessages: StateFlow<List<ProviderMessage>> = selectedNovelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else novelRepository.observeAssistantSession(id).map { session ->
            session?.messagesJson?.let {
                runCatching { container.json.decodeFromString<List<ProviderMessage>>(it) }.getOrDefault(emptyList())
            }.orEmpty()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var autosaveJob: Job? = null
    private var activeGenerationJob: Job? = null
    private var pendingAutosave: Pair<String, String>? = null
    private val generationSettingsMutex = Mutex()

    init {
        viewModelScope.launch { generationRepository.recoverInterruptedGenerationState() }
        viewModelScope.launch {
            selectedNovelId.collectLatest { novelId ->
                val current = novelId?.let { novelRepository.novel(it) }
                bodyGenerationSettings.value = current?.bodyGenerationSettingsJson.decodeGenerationSettingsOrDefault()
                summaryGenerationSettings.value = current?.summaryGenerationSettingsJson.decodeGenerationSettingsOrDefault()
                assistantGenerationSettings.value = current?.assistantGenerationSettingsJson.decodeGenerationSettingsOrDefault()
            }
        }
        viewModelScope.launch {
            generationRepository.progress.collect { progress ->
                isBusy.value = progress is GenerationProgress.Started ||
                    progress is GenerationProgress.Delta ||
                    progress is GenerationProgress.AssistantStarted ||
                    progress is GenerationProgress.AssistantDelta
                when (progress) {
                    is GenerationProgress.AssistantStarted -> assistantLive.value = AssistantLiveUiState(active = true)
                    is GenerationProgress.AssistantDelta -> assistantLive.update { current ->
                        current.copy(
                            active = true,
                            content = current.content + progress.content,
                            reasoning = current.reasoning + progress.reasoning.orEmpty(),
                        )
                    }
                    is GenerationProgress.AssistantCompleted -> assistantLive.value = AssistantLiveUiState()
                    is GenerationProgress.Failed -> {
                        _messages.emit(UiMessage.Error(progress.message))
                    }
                    is GenerationProgress.Completed -> {
                        _messages.emit(
                            UiMessage.Info(if (progress.complete) "新的候选已经准备好，还没有写入正文" else "生成停在半途，已为你保留当前内容"),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    fun createNovel(name: String) = launchAction("新故事没有创建成功") {
        val created = novelRepository.createNovel(name)
        selectedNovelId.value = created.id
        candidatePanelExpanded.value = true
        destination.value = AppDestination.WORKSPACE
        destinationBeforeSettings = AppDestination.WORKSPACE
    }

    fun selectNovel(novelId: String) {
        selectedNovelId.value = novelId
        selectedChapterId.value = null
        candidatePanelExpanded.value = true
        destination.value = AppDestination.WORKSPACE
        destinationBeforeSettings = AppDestination.WORKSPACE
    }

    suspend fun decodeCoverForEditing(uri: Uri): Bitmap = container.coverStorage.decodeForEditing(uri)

    fun coverFile(fileName: String?): File? = container.coverStorage.file(fileName)

    fun saveNovelCover(novelId: String, bitmap: Bitmap, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val fileName = container.coverStorage.save(novelId, bitmap)
                novelRepository.updateNovelCover(novelId, fileName)
                _messages.emit(UiMessage.Info("新封面已经换好了"))
                onComplete(true)
            } catch (error: Exception) {
                _messages.emit(UiMessage.Error("封面没有保存成功：${error.message ?: "未知错误"}"))
                onComplete(false)
            }
        }
    }

    fun openDestination(target: AppDestination) {
        if (target == AppDestination.SETTINGS && destination.value != AppDestination.SETTINGS) {
            destinationBeforeSettings = destination.value
        }
        destination.value = target
    }

    fun closeSettings() {
        destination.value = destinationBeforeSettings
    }

    fun returnToLibrary() {
        viewModelScope.launch {
            runCatching { flushAutosaveNow() }
                .onFailure { error ->
                    _messages.emit(UiMessage.Error("正文还没能保存，请稍后再返回书架：${error.message ?: "未知错误"}"))
                    return@launch
                }
            preparedRequest.value = null
            creativeRequirement.value = ""
            candidatePanelExpanded.value = true
            selectedNovelId.value = null
            selectedChapterId.value = null
            destination.value = AppDestination.WORKSPACE
            destinationBeforeSettings = AppDestination.WORKSPACE
        }
    }

    fun showCandidatePanel() {
        candidatePanelExpanded.value = true
    }

    fun hideCandidatePanel() {
        candidatePanelExpanded.value = false
    }

    fun selectChapter(chapterId: String) {
        viewModelScope.launch {
            flushAutosaveNow()
            selectedChapterId.value = chapterId
        }
    }

    fun updateChapterDraft(content: String) {
        val chapter = workspace.value.selectedChapter ?: return
        pendingAutosave = chapter.id to content
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            runCatching {
                novelRepository.saveChapterContent(chapter.id, content)
                if (pendingAutosave == (chapter.id to content)) pendingAutosave = null
            }
                .onFailure { _messages.emit(UiMessage.Error("这次修改还没能自动保存：${it.message}")) }
        }
    }

    fun flushPendingAutosave() {
        viewModelScope.launch { flushAutosaveNow() }
    }

    fun createNextChapter() = launchAction("创建下一章失败") {
        flushAutosaveNow()
        val novelId = requireNotNull(selectedNovelId.value)
        val chapter = novelRepository.createNextChapter(novelId)
        selectedChapterId.value = chapter.id
    }

    fun createNextChapterAndInspect() = launchAction("新章节没有准备成功") {
        val requirement = creativeRequirement.value.trim()
        require(requirement.isNotEmpty()) { "请先填写本次创作要求" }
        flushAutosaveNow()
        val currentNovel = novelRepository.novel(requireNotNull(selectedNovelId.value)) ?: error("小说不存在")
        requireNotNull(currentNovel.bodyProviderId) { "请先配置正文模型" }
        requireNotNull(currentNovel.bodyModelId) { "请先配置正文模型" }
        val currentChapter = workspace.value.selectedChapter?.id?.let { novelRepository.chapter(it) }
        val lastChapter = workspace.value.chapters.maxByOrNull { it.storyOrder }
        val reuseEmptyLastChapter = currentChapter != null &&
            currentChapter.id == lastChapter?.id &&
            currentChapter.content.isBlank()
        val target = if (
            reuseEmptyLastChapter
        ) {
            requireNotNull(currentChapter)
        } else {
            novelRepository.createNextChapter(currentNovel.id)
        }
        try {
            val prepared = generationRepository.generateBody(
                currentNovel.id,
                target.id,
                requirement,
                bodyGenerationSettings.value,
                inspectOnly = true,
            )
            selectedChapterId.value = target.id
            pendingBodyRequirement.value = requirement
            preparedRequest.value = prepared
        } catch (error: Exception) {
            if (!reuseEmptyLastChapter) novelRepository.deleteEmptyChapter(target.id)
            throw error
        }
        _messages.emit(UiMessage.Info("新章节已经展开，确认发送内容后就会继续创作"))
    }

    fun renameChapter(chapterId: String, name: String?) = launchAction("重命名失败") {
        novelRepository.renameChapter(chapterId, name)
    }

    fun softDeleteChapter(chapterId: String) = launchAction("删除章节失败") {
        flushAutosaveNow()
        val activeCount = workspace.value.chapters.size
        require(activeCount > 1) { "至少保留一个可编辑章节" }
        novelRepository.softDeleteChapter(chapterId)
        if (selectedChapterId.value == chapterId) selectedChapterId.value = null
        _messages.emit(UiMessage.Info("章节已经收进回收区，依赖它的总结需要重新整理"))
    }

    fun restoreChapter(chapterId: String) = launchAction("恢复章节失败") {
        novelRepository.restoreChapter(chapterId)
        selectedChapterId.value = chapterId
        _messages.emit(UiMessage.Info("章节已经回来了；相关总结仍需要重新整理"))
    }

    fun permanentlyDeleteChapter(chapterId: String) = launchAction("彻底删除章节失败") {
        flushAutosaveNow()
        novelRepository.permanentlyDeleteChapter(chapterId)
        if (selectedChapterId.value == chapterId) selectedChapterId.value = null
        _messages.emit(UiMessage.Info("章节已彻底删除"))
    }

    fun moveChapter(chapterId: String, direction: Int) = launchAction("调整章节顺序失败") {
        flushAutosaveNow()
        val active = workspace.value.chapters
        val index = active.indexOfFirst { it.id == chapterId }
        val otherIndex = index + direction
        require(index >= 0 && otherIndex in active.indices) { "章节已经在边界" }
        novelRepository.swapChapterOrder(chapterId, active[otherIndex].id)
        _messages.emit(UiMessage.Info("故事顺序已经更新，受影响的总结需要重新整理"))
    }

    fun inspectBodyRequest() = launchAction("请求检查失败") {
        flushAutosaveNow()
        val novelId = requireNotNull(selectedNovelId.value)
        val chapterId = requireNotNull(workspace.value.selectedChapter?.id)
        val requirement = creativeRequirement.value.trim()
        require(requirement.isNotEmpty()) { "请先填写本次创作要求" }
        pendingBodyRequirement.value = requirement
        preparedRequest.value = generationRepository.generateBody(
            novelId,
            chapterId,
            requirement,
            bodyGenerationSettings.value,
            inspectOnly = true,
        )
    }

    fun sendPreparedRequest() {
        if (activeGenerationJob?.isActive == true) return
        val prepared = preparedRequest.value ?: return
        preparedRequest.value = null
        if (prepared.taskType == TaskType.BODY || prepared.taskType == TaskType.SUMMARY) {
            candidatePanelExpanded.value = true
        }
        activeGenerationJob = viewModelScope.launch {
            isBusy.value = true
            try {
                when (prepared.taskType) {
                    TaskType.BODY -> generationRepository.sendPreparedBody(prepared, requireNotNull(pendingBodyRequirement.value))
                    TaskType.SUMMARY -> generationRepository.sendPreparedSummary(prepared, pendingSummarySelections.value)
                    TaskType.ASSISTANT -> generationRepository.sendPreparedAssistant(
                        prepared,
                        pendingAssistantPreviousMessages.value,
                        requireNotNull(pendingAssistantMessage.value),
                    )
                    TaskType.MODEL_VALIDATION -> error("该请求不能从这里发送")
                }
            } catch (error: CancellationException) {
                if (error.message != DELETE_CANDIDATE_CANCELLATION) {
                    _messages.emit(UiMessage.Info("已经停下生成，收到的内容为你保留在候选中"))
                }
            } catch (error: Exception) {
                _messages.emit(UiMessage.Error("发送请求失败：${error.message ?: "未知错误"}"))
            } finally {
                pendingSummarySelections.value = emptyList()
                pendingBodyRequirement.value = null
                pendingAssistantMessage.value = null
                pendingAssistantPreviousMessages.value = emptyList()
                isBusy.value = false
                if (prepared.taskType == TaskType.ASSISTANT) assistantLive.value = AssistantLiveUiState()
                activeGenerationJob = null
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel(CancellationException("用户停止生成"))
    }

    fun closePreparedRequest() {
        preparedRequest.value = null
        pendingBodyRequirement.value = null
        pendingSummarySelections.value = emptyList()
        pendingAssistantMessage.value = null
        pendingAssistantPreviousMessages.value = emptyList()
    }

    fun editCandidate(candidateId: String, text: String) {
        if (activeGenerationJob?.isActive == true) return
        viewModelScope.launch {
            runCatching { generationRepository.editCandidate(candidateId, text) }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _messages.emit(UiMessage.Error("保存候选失败：${error.message ?: "未知错误"}"))
                    }
                }
        }
    }

    fun acceptCandidate(candidateId: String) = launchAction("接受候选失败") {
        require(activeGenerationJob?.isActive != true) { "请先停止生成或等待生成完成" }
        flushAutosaveNow()
        val group = workspace.value.activeGroups.firstOrNull()
        if (group?.taskType == TaskType.SUMMARY) {
            novelRepository.acceptSummaryCandidate(candidateId)
            _messages.emit(UiMessage.Info("总结已经采用，原始内容已从下一次创作中退出，避免重复"))
        } else {
            novelRepository.acceptBodyCandidate(candidateId)
            if (creativeRequirement.value.trim() == group?.oneTimeCreativeRequirement.orEmpty().trim()) {
                creativeRequirement.value = ""
            }
            _messages.emit(UiMessage.Info("已经续写到章节末尾，需要时可以在“足迹”里撤销"))
        }
    }

    fun discardGroup(groupId: String) = launchAction("放弃候选失败") {
        require(activeGenerationJob?.isActive != true) { "请先停止生成或等待生成完成" }
        val group = workspace.value.activeGroups.firstOrNull { it.id == groupId }
        novelRepository.discardCandidateGroup(groupId)
        if (group?.taskType == TaskType.BODY && creativeRequirement.value.trim() == group.oneTimeCreativeRequirement.orEmpty().trim()) {
            creativeRequirement.value = ""
        }
        _messages.emit(UiMessage.Info("候选已放下，正文没有改变"))
    }

    fun deleteCandidate(candidateId: String) {
        viewModelScope.launch {
            try {
                if (activeGenerationJob?.isActive == true && generationRepository.activeCandidateId.value == candidateId) {
                    activeGenerationJob?.let { job ->
                        job.cancel(CancellationException(DELETE_CANDIDATE_CANCELLATION))
                        job.join()
                    }
                }
                val result = novelRepository.deleteCandidate(candidateId)
                _messages.emit(
                    UiMessage.Info(
                        if (result.groupDiscarded) "这个候选已经移除，这一组没有其他候选了"
                        else "这个候选已经移除，还剩 ${result.remainingCount} 个",
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.emit(UiMessage.Error("删除候选失败：${error.message ?: "未知错误"}"))
            }
        }
    }

    fun deleteAllCandidates(groupId: String) {
        viewModelScope.launch {
            try {
                if (activeGenerationJob?.isActive == true) {
                    activeGenerationJob?.let { job ->
                        job.cancel(CancellationException(DELETE_CANDIDATE_CANCELLATION))
                        job.join()
                    }
                }
                val result = novelRepository.deleteAllCandidates(groupId)
                _messages.emit(UiMessage.Info("已移除 ${result.deletedCount} 个候选，正文没有改变"))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.emit(UiMessage.Error("删除全部候选失败：${error.message ?: "未知错误"}"))
            }
        }
    }

    fun roll(groupId: String) {
        if (activeGenerationJob?.isActive == true) return
        activeGenerationJob = viewModelScope.launch {
            isBusy.value = true
            try {
                generationRepository.roll(groupId)
            } catch (error: CancellationException) {
                if (error.message != DELETE_CANDIDATE_CANCELLATION) {
                    _messages.emit(UiMessage.Info("已经停下重新生成，收到的内容为你保留在候选中"))
                }
            } catch (error: Exception) {
                _messages.emit(UiMessage.Error("重新生成没有完成：${error.message ?: "未知错误"}"))
            } finally {
                isBusy.value = false
                activeGenerationJob = null
            }
        }
    }

    fun saveProvider(
        providerId: String,
        apiKey: String,
        baseUrl: String,
        modelId: String?,
        onSuccess: () -> Unit = {},
    ) = launchAction("连接验证失败") {
        val definition = ProviderCatalog.find(providerId) ?: error("未知厂商")
        val result = generationRepository.saveAndValidateProvider(
            providerId = providerId,
            displayName = definition.displayName,
            baseUrl = baseUrl,
            apiKey = apiKey,
            preferredModelId = modelId,
        )
        val novelId = selectedNovelId.value
        if (novelId != null) {
            val current = novelRepository.novel(novelId) ?: error("小说不存在")
            novelRepository.updateNovelModels(
                novelId = novelId,
                bodyProviderId = current.bodyProviderId ?: providerId,
                bodyModelId = current.bodyModelId ?: result.selectedModelId,
                summaryProviderId = current.summaryProviderId ?: providerId,
                summaryModelId = current.summaryModelId ?: result.selectedModelId,
                assistantProviderId = current.assistantProviderId ?: providerId,
                assistantModelId = current.assistantModelId ?: result.selectedModelId,
            )
        }
        _messages.emit(UiMessage.Info("连接成功，已获取 ${result.models.size} 个模型"))
        onSuccess()
    }

    fun refreshModels(providerId: String) = launchAction("刷新模型失败") {
        val models = generationRepository.refreshModels(providerId)
        _messages.emit(UiMessage.Info("已刷新 ${models.size} 个模型，自定义上下文上限保持不变"))
    }

    fun addManualModel(providerId: String, modelId: String, onSuccess: () -> Unit = {}) = launchAction("验证手动模型失败") {
        val model = generationRepository.validateManualModel(providerId, modelId)
        selectedNovelId.value?.let { novelId ->
            val current = novelRepository.novel(novelId) ?: error("小说不存在")
            novelRepository.updateNovelModels(
                novelId = novelId,
                bodyProviderId = current.bodyProviderId ?: providerId,
                bodyModelId = current.bodyModelId ?: model.modelId,
                summaryProviderId = current.summaryProviderId ?: providerId,
                summaryModelId = current.summaryModelId ?: model.modelId,
                assistantProviderId = current.assistantProviderId ?: providerId,
                assistantModelId = current.assistantModelId ?: model.modelId,
            )
        }
        _messages.emit(UiMessage.Info("模型 ${model.modelId} 已通过真实生成验证"))
        onSuccess()
    }

    fun selectTaskModel(taskType: TaskType, providerId: String, modelId: String) = launchAction("保存模型失败") {
        val novelId = requireNotNull(selectedNovelId.value)
        val current = novelRepository.novel(novelId) ?: error("小说不存在")
        val resolvedBodyModel = when {
            taskType == TaskType.BODY -> modelId
            current.bodyModelId != null -> current.bodyModelId
            else -> error("请先选择正文模型")
        }
        novelRepository.updateNovelModels(
            novelId = novelId,
            bodyProviderId = if (taskType == TaskType.BODY) providerId else current.bodyProviderId ?: providerId,
            bodyModelId = resolvedBodyModel,
            summaryProviderId = if (taskType == TaskType.SUMMARY) providerId else current.summaryProviderId,
            summaryModelId = if (taskType == TaskType.SUMMARY) modelId else current.summaryModelId,
            assistantProviderId = if (taskType == TaskType.ASSISTANT) providerId else current.assistantProviderId,
            assistantModelId = if (taskType == TaskType.ASSISTANT) modelId else current.assistantModelId,
        )
    }

    fun updateGenerationSettings(taskType: TaskType, settings: GenerationSettings) {
        val normalized = if (settings.thinkingEnabled) settings else settings.copy(streamThinking = false)
        when (taskType) {
            TaskType.BODY -> bodyGenerationSettings.value = normalized
            TaskType.SUMMARY -> summaryGenerationSettings.value = normalized
            TaskType.ASSISTANT -> assistantGenerationSettings.value = normalized
            TaskType.MODEL_VALIDATION -> Unit
        }
        val novelId = selectedNovelId.value ?: return
        val bodySnapshot = bodyGenerationSettings.value
        val summarySnapshot = summaryGenerationSettings.value
        val assistantSnapshot = assistantGenerationSettings.value
        viewModelScope.launch {
            generationSettingsMutex.withLock {
                runCatching {
                    novelRepository.updateGenerationSettings(
                        novelId,
                        bodySnapshot,
                        summarySnapshot,
                        assistantSnapshot,
                    )
                }.onFailure { error ->
                    _messages.emit(UiMessage.Error("保存生成参数失败：${error.message ?: "未知错误"}"))
                }
            }
        }
    }

    fun updateNovelInstructions(summaryInstruction: String, assistantInstruction: String) =
        launchAction("保存独立指令失败") {
            novelRepository.updateNovelInstructions(
                requireNotNull(selectedNovelId.value),
                summaryInstruction,
                assistantInstruction,
            )
            _messages.emit(UiMessage.Info("本作指令已保存"))
        }

    fun saveSummaryAsAppDefault() = launchAction("保存应用默认指令失败") {
        novelRepository.saveNovelSummaryAsAppDefault(requireNotNull(selectedNovelId.value))
        _messages.emit(UiMessage.Info("当前总结指令将用于以后新建的小说"))
    }

    fun resetNovelSummaryToAppDefault() = launchAction("重置总结指令失败") {
        novelRepository.resetNovelSummaryToAppDefault(requireNotNull(selectedNovelId.value))
        _messages.emit(UiMessage.Info("本作总结指令已重置为当前应用默认值"))
    }

    fun resetAppSummaryDefaultToFactory() = launchAction("恢复出厂指令失败") {
        novelRepository.resetAppSummaryDefaultToFactory()
        _messages.emit(UiMessage.Info("以后新建小说使用的总结指令已恢复出厂版本"))
    }

    fun setMaxContext(providerId: String, modelId: String, value: String) = launchAction("保存上下文上限失败") {
        val tokens = value.toLongOrNull() ?: error("请输入大于零的整数")
        novelRepository.setMaxContext(providerId, modelId, tokens)
        _messages.emit(UiMessage.Info("最大上下文已保存，刷新模型不会覆盖"))
    }

    fun revealSavedApiKey(providerId: String, onLoaded: (String?) -> Unit) {
        onLoaded(generationRepository.readSavedApiKey(providerId))
    }

    fun addContextEntry(entry: ContextEntryEntity) = launchAction("保存条目失败") {
        novelRepository.upsertContextEntry(entry)
    }

    fun deleteContextEntry(entry: ContextEntryEntity) = launchAction("删除条目失败") {
        novelRepository.deleteContextEntry(entry)
    }

    fun copyContextEntryToNovel(entry: ContextEntryEntity) = launchAction("复制条目失败") {
        novelRepository.copyContextEntry(
            entry.id,
            EntryScope.NOVEL,
            requireNotNull(selectedNovelId.value),
        )
        _messages.emit(UiMessage.Info("已经放进当前作品，并会陪伴下一次创作"))
    }

    fun copyContextEntryToGlobal(entry: ContextEntryEntity) = launchAction("复制条目失败") {
        novelRepository.copyContextEntry(entry.id, EntryScope.GLOBAL, null)
        _messages.emit(UiMessage.Info("已经收进灵感素材库，不会自行参与任何作品"))
    }

    fun swapContextEntries(firstId: String, secondId: String) = launchAction("调整顺序失败") {
        novelRepository.swapContextEntries(firstId, secondId)
    }

    fun toggleChapterContext(chapter: ChapterEntity) = launchAction("更新上下文失败") {
        novelRepository.setChapterIncluded(chapter.id, !chapter.includeInContext)
    }

    fun toggleSummaryContext(summary: SummaryEntity) = launchAction("更新上下文失败") {
        novelRepository.setSummaryIncluded(summary.id, !summary.includeInContext)
    }

    fun updateSummary(summaryId: String, name: String, content: String) = launchAction("保存总结失败") {
        novelRepository.updateSummary(summaryId, name, content)
    }

    fun deleteSummary(summaryId: String) = launchAction("删除总结失败") {
        novelRepository.deleteSummary(summaryId)
        _messages.emit(UiMessage.Info("总结已删除"))
    }

    fun loadDefaultSummarySources() = launchAction("读取总结来源失败") {
        val novelId = requireNotNull(selectedNovelId.value)
        val selections = contextAssembler.computeDefaultSummarySources(novelId)
        summarySourceSelections.value = selections
    }

    fun toggleSummarySource(nodeId: String, nodeType: ContextNodeType) {
        val selection = SummarySourceSelection(nodeId, nodeType)
        summarySourceSelections.update { current ->
            if (selection in current) current - selection else current + selection
        }
    }

    fun inspectSelectedSummaryRequest() = launchAction("总结请求检查失败") {
        val novelId = requireNotNull(selectedNovelId.value)
        val selections = summarySourceSelections.value
        require(selections.isNotEmpty()) { "请至少选择一个总结来源" }
        pendingSummarySelections.value = selections
        preparedRequest.value = generationRepository.generateSummary(
            novelId,
            selections,
            summaryDensity.value,
            summaryGenerationSettings.value,
            inspectOnly = true,
        )
    }

    fun previewHistoryCleanup(
        taskType: TaskType?,
        olderThan: Long?,
        onPreview: (HistoryCleanupPreview) -> Unit,
    ) = launchAction("清理预览失败") {
        onPreview(novelRepository.previewHistoryCleanup(requireNotNull(selectedNovelId.value), taskType, olderThan))
    }

    fun cleanupHistory(taskType: TaskType?, olderThan: Long?) = launchAction("清理历史失败") {
        val result = novelRepository.cleanupHistory(requireNotNull(selectedNovelId.value), taskType, olderThan)
        _messages.emit(
            UiMessage.Info(
                "已清理 ${result.deletedRequests} 条创作记录、${result.deletedCandidates} 个未采用候选和 ${result.deletedUsage} 条用量记录；另有 ${result.blockedRequests} 条仍与正文或版本相关，已经为你保留",
            ),
        )
    }

    fun inspectAssistantRequest(message: String) = launchAction("助手请求检查失败") {
        require(message.isNotBlank()) { "请输入要讨论的问题" }
        pendingAssistantMessage.value = message.trim()
        pendingAssistantPreviousMessages.value = assistantMessages.value
        preparedRequest.value = generationRepository.generateAssistantReply(
            requireNotNull(selectedNovelId.value),
            message,
            assistantGenerationSettings.value,
            inspectOnly = true,
        )
    }

    fun resetAssistant() = launchAction("重置助手失败") {
        novelRepository.resetAssistantSession(requireNotNull(selectedNovelId.value))
        _messages.emit(UiMessage.Info("这段对话和相关记录已经清空"))
    }

    fun notifyCopiedToClipboard(message: String = "灵感内容已复制到剪贴板") {
        viewModelScope.launch {
            _messages.emit(UiMessage.Info(message))
        }
    }

    fun saveAssistantIdea(
        name: String,
        content: String,
        scope: EntryScope,
        type: EntryType = EntryType.WORLDBOOK,
        enabledForNovel: Boolean = true,
    ) = launchAction("保存灵感失败") {
        val entry = buildAssistantIdeaEntry(
            name = name,
            content = content,
            scope = scope,
            type = type,
            novelId = selectedNovelId.value,
            enabledForNovel = enabledForNovel,
        )
        novelRepository.upsertContextEntry(entry)
        _messages.emit(UiMessage.Info(buildAssistantIdeaInfoMessage(scope, type, enabledForNovel)))
    }

    fun applyAssistantIdeaAsRequirement(
        requirementText: String,
        alsoAddToNovelWorldbook: Boolean = false,
        worldbookName: String = "",
        navigateToWorkspace: Boolean = true,
    ) = launchAction("填入本章要求失败") {
        require(requirementText.trim().isNotEmpty()) { "请输入本次创作要求" }
        if (alsoAddToNovelWorldbook) {
            require(worldbookName.trim().isNotEmpty()) { "请输入世界书条目名称" }
            val novelId = requireNotNull(selectedNovelId.value) { "请先选择作品" }
            novelRepository.upsertContextEntry(
                ContextEntryEntity(
                    type = EntryType.WORLDBOOK,
                    scope = EntryScope.NOVEL,
                    novelId = novelId,
                    name = worldbookName.trim(),
                    content = requirementText.trim(),
                    enabled = true,
                    sortOrder = System.currentTimeMillis(),
                ),
            )
        }
        creativeRequirement.value = requirementText.trim()
        candidatePanelExpanded.value = false
        if (navigateToWorkspace) {
            destination.value = AppDestination.WORKSPACE
        }
        val infoMessage = if (alsoAddToNovelWorldbook) {
            "已填入本章创作要求，并同步加入本作世界书"
        } else {
            "已填入本章创作要求"
        }
        _messages.emit(UiMessage.Info(infoMessage))
    }

    fun undoAcceptance(acceptanceId: String) = launchAction("撤销接受失败") {
        when (val result = novelRepository.undoAcceptance(acceptanceId)) {
            com.ainovel.local.domain.UndoAcceptResult.Success -> _messages.emit(UiMessage.Info("这次续写已从正文中撤回，创作记录仍然保留"))
            com.ainovel.local.domain.UndoAcceptResult.AlreadyUndone -> _messages.emit(UiMessage.Info("这次续写之前已经撤回了"))
            is com.ainovel.local.domain.UndoAcceptResult.Conflict -> _messages.emit(
                UiMessage.Error("正文后来又有修改。为了不误删后续文字，这次没有自动撤回"),
            )
            com.ainovel.local.domain.UndoAcceptResult.NotFound -> error("接受记录不存在")
        }
    }

    fun createBackup(output: OutputStream, password: CharArray) = launchAction("备份失败") {
        flushAutosaveNow()
        container.backupRepository.createBackup(output, password)
        _messages.emit(UiMessage.Info("完整备份已创建"))
    }

    fun createBackup(uri: Uri, password: CharArray) = launchAction("备份失败") {
        flushAutosaveNow()
        val resolver = getApplication<Application>().contentResolver
        resolver.openOutputStream(uri, "w")?.use { output ->
            container.backupRepository.createBackup(output, password)
        } ?: error("无法写入选定位置")
        _messages.emit(UiMessage.Info("完整加密备份已创建"))
    }

    fun exportNovelEpub(output: OutputStream, novelId: String? = selectedNovelId.value) = launchAction("导出 EPUB 失败") {
        flushAutosaveNow()
        val targetNovelId = novelId ?: error("请先选择作品")
        val novel = novelRepository.novel(targetNovelId) ?: error("作品不存在")
        val chapters = novelRepository.allChapters(targetNovelId)
        val exportableChapters = EpubExporter.filterExportableChapters(chapters)
        withContext(Dispatchers.IO) {
            val coverBytes = novel.coverFileName?.let { fileName ->
                container.coverStorage.file(fileName)?.takeIf { it.exists() }?.readBytes()
            }
            output.use { stream ->
                EpubExporter.export(
                    novel = novel,
                    chapters = exportableChapters,
                    coverBytes = coverBytes,
                    output = stream,
                )
            }
        }
        _messages.emit(UiMessage.Info("EPUB 电子书已导出"))
    }

    fun exportNovelEpub(uri: Uri, novelId: String? = selectedNovelId.value) = launchAction("导出 EPUB 失败") {
        flushAutosaveNow()
        val targetNovelId = novelId ?: error("请先选择作品")
        val novel = novelRepository.novel(targetNovelId) ?: error("作品不存在")
        val chapters = novelRepository.allChapters(targetNovelId)
        val exportableChapters = EpubExporter.filterExportableChapters(chapters)
        withContext(Dispatchers.IO) {
            val coverBytes = novel.coverFileName?.let { fileName ->
                container.coverStorage.file(fileName)?.takeIf { it.exists() }?.readBytes()
            }
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(uri, "w")?.use { output ->
                EpubExporter.export(
                    novel = novel,
                    chapters = exportableChapters,
                    coverBytes = coverBytes,
                    output = output,
                )
            } ?: error("无法写入选定位置")
        }
        _messages.emit(UiMessage.Info("EPUB 电子书已导出"))
    }

    fun previewBackup(
        input: InputStream,
        password: CharArray,
        onPreview: (BackupRestorePreview) -> Unit,
    ) = launchAction("读取备份失败") {
        onPreview(container.backupRepository.preview(input, password))
    }

    fun previewBackup(
        uri: Uri,
        password: CharArray,
        onPreview: (BackupRestorePreview) -> Unit,
    ) = launchAction("读取备份失败") {
        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { input ->
            onPreview(container.backupRepository.preview(input, password))
        } ?: error("无法读取选定文件")
    }

    fun restoreBackup(input: InputStream, password: CharArray) = launchAction("恢复失败") {
        container.backupRepository.restore(input, password)
        rollbackBackupAvailable.value = true
        selectedNovelId.value = null
        selectedChapterId.value = null
        _messages.emit(UiMessage.Info("完整恢复成功；恢复前数据已保留为加密回滚备份"))
    }

    fun restoreBackup(uri: Uri, password: CharArray) = launchAction("恢复失败") {
        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { input -> container.backupRepository.restore(input, password) }
            ?: error("无法读取选定文件")
        rollbackBackupAvailable.value = true
        selectedNovelId.value = null
        selectedChapterId.value = null
        _messages.emit(UiMessage.Info("完整恢复成功；恢复前数据已保留为加密回滚备份"))
    }

    fun restoreRollbackBackup(password: CharArray) = launchAction("回滚恢复失败") {
        val file = container.backupRepository.rollbackFile()
        require(file.exists()) { "没有可用的恢复前备份" }
        file.inputStream().use { input -> container.backupRepository.restore(input, password) }
        rollbackBackupAvailable.value = true
        selectedNovelId.value = null
        selectedChapterId.value = null
        _messages.emit(UiMessage.Info("已恢复上次覆盖前的数据"))
    }

    private fun launchAction(errorPrefix: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            isBusy.value = true
            runCatching { block() }
                .onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        _messages.emit(UiMessage.Error("$errorPrefix：${error.message ?: "未知错误"}"))
                    }
                }
            isBusy.value = false
        }
    }

    private suspend fun flushAutosaveNow() {
        autosaveJob?.cancel()
        autosaveJob = null
        val pending = pendingAutosave ?: return
        novelRepository.saveChapterContent(pending.first, pending.second)
        if (pendingAutosave == pending) pendingAutosave = null
    }

    private fun String?.decodeGenerationSettingsOrDefault(): GenerationSettings =
        this?.let { encoded ->
            runCatching { container.json.decodeFromString<GenerationSettings>(encoded) }.getOrNull()
        } ?: GenerationSettings()

    private companion object {
        const val DELETE_CANDIDATE_CANCELLATION = "delete-candidate"
    }
}

internal fun buildAssistantIdeaEntry(
    name: String,
    content: String,
    scope: EntryScope,
    type: EntryType = EntryType.WORLDBOOK,
    novelId: String? = null,
    enabledForNovel: Boolean = true,
): ContextEntryEntity {
    require(name.trim().isNotEmpty()) { "请输入给自己看的名字" }
    require(content.trim().isNotEmpty()) { "请输入灵感内容" }
    val resolvedNovelId = if (scope == EntryScope.NOVEL) {
        requireNotNull(novelId) { "请先选择作品" }
    } else {
        null
    }
    return ContextEntryEntity(
        type = type,
        scope = scope,
        novelId = resolvedNovelId,
        name = name.trim(),
        content = content.trim(),
        enabled = if (scope == EntryScope.NOVEL) enabledForNovel else false,
        sortOrder = System.currentTimeMillis(),
    )
}

internal fun buildAssistantIdeaInfoMessage(
    scope: EntryScope,
    type: EntryType = EntryType.WORLDBOOK,
    enabledForNovel: Boolean = true,
): String = when {
    scope == EntryScope.GLOBAL -> "已收入灵感素材库，可在“记忆 · 灵感素材”中查看"
    scope == EntryScope.NOVEL && enabledForNovel -> {
        if (type == EntryType.PRESET) "已加入本作预设，将陪伴下一次创作"
        else "已加入本作世界书，将陪伴下一次创作"
    }
    else -> "已存入本作，暂未启用"
}

internal fun resolveActiveCandidates(
    activeGroups: List<CandidateGroupEntity>,
    candidates: List<CandidateEntity>,
): List<CandidateEntity> {
    val activeGroupId = activeGroups.firstOrNull()?.id ?: return emptyList()
    return candidates.filter { it.candidateGroupId == activeGroupId }
}

