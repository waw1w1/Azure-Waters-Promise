package com.ainovel.local.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.ainovel.local.ui.localization.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.model.UsageRecordEntity
import com.ainovel.local.domain.EpubExporter
import com.ainovel.local.ui.AppViewModel
import com.ainovel.local.ui.WorkspaceUiState
import com.ainovel.local.ui.components.PrimaryAction
import com.ainovel.local.ui.components.SecondaryAction
import com.ainovel.local.ui.components.StatusPill
import com.ainovel.local.ui.components.StatusTone
import kotlinx.coroutines.launch

internal const val CHAPTER_RAIL_EXPANDED_WIDTH_DP = 332

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(viewModel: AppViewModel, imeVisible: Boolean) {
    val state by viewModel.workspace.collectAsStateWithLifecycle()
    val requirement by viewModel.creativeRequirement.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    val candidatePanelExpanded by viewModel.candidatePanelExpanded.collectAsStateWithLifecycle()
    val bodySettings by viewModel.bodyGenerationSettings.collectAsStateWithLifecycle()
    val summarySettings by viewModel.summaryGenerationSettings.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { windowSize.width.toDp() }
    val windowHeight = with(density) { windowSize.height.toDp() }
    val expanded = windowWidth >= 840.dp && windowHeight >= 600.dp
    val shortViewport = windowHeight < 520.dp
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var chapterRailExpanded by rememberSaveable { mutableStateOf(false) }
    var manuscriptFocused by remember { mutableStateOf(false) }
    val hideDockForManuscriptKeyboard = manuscriptFocused && imeVisible
    val activeGroup = state.activeGroups.firstOrNull()

    val exportEpubLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { uri ->
        if (uri != null) viewModel.exportNovelEpub(uri)
    }
    val onExportEpub = {
        exportEpubLauncher.launch(EpubExporter.buildSuggestedFileName(state.novel?.name ?: "novel"))
    }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = chapterRailExpanded,
                enter = fadeIn(tween(200)) + slideInHorizontally(tween(240)) { -it / 3 },
                exit = fadeOut(tween(130)) + slideOutHorizontally(tween(160)) { -it / 4 },
            ) {
                Row {
                    ChapterRail(
                        state = state,
                        deletedChapters = allChapters.filter { it.deleted },
                        onSelect = viewModel::selectChapter,
                        onAdd = viewModel::createNextChapter,
                        onMove = viewModel::moveChapter,
                        onDelete = viewModel::softDeleteChapter,
                        onRestore = viewModel::restoreChapter,
                        onPermanentDelete = viewModel::permanentlyDeleteChapter,
                        onExportEpub = onExportEpub,
                        modifier = Modifier.width(CHAPTER_RAIL_EXPANDED_WIDTH_DP.dp).fillMaxHeight(),
                    )
                    VerticalDivider()
                }
            }
            Column(Modifier.weight(1.5f).fillMaxHeight()) {
                ManuscriptPane(
                    state = state,
                    onContentChange = viewModel::updateChapterDraft,
                    onRename = viewModel::renameChapter,
                    onEditorFocusChange = { manuscriptFocused = it },
                    onOpenChapters = { focusManager.clearFocus(); keyboardController?.hide(); chapterRailExpanded = !chapterRailExpanded },
                    chaptersExpanded = chapterRailExpanded,
                    onExportEpub = onExportEpub,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if ((state.candidates.isEmpty() || !candidatePanelExpanded || activeGroup == null) && !hideDockForManuscriptKeyboard) {
                    GenerationDock(
                        requirement = requirement,
                        onRequirementChange = { viewModel.creativeRequirement.value = it },
                        onInspect = viewModel::inspectBodyRequest,
                        busy = busy,
                        modelLabel = state.novel?.bodyModelId ?: "尚未配置模型",
                        failedGroup = state.activeGroups.firstOrNull { it.status == com.ainovel.local.data.model.CandidateGroupStatus.FAILED },
                        onRetry = viewModel::roll,
                        onDiscard = viewModel::discardGroup,
                        onStop = viewModel::stopGeneration,
                        onCreateNextChapter = viewModel::createNextChapterAndInspect,
                        pendingCandidateCount = state.candidates.size,
                        onShowCandidates = viewModel::showCandidatePanel,
                        onDeleteAllCandidates = { activeGroup?.let { viewModel.deleteAllCandidates(it.id) } },
                    )
                }
            }
            if (activeGroup != null && state.candidates.isNotEmpty() && candidatePanelExpanded) {
                VerticalDivider()
                CandidatePanel(
                    group = activeGroup,
                    candidates = state.candidates,
                    onEdit = viewModel::editCandidate,
                    onRoll = viewModel::roll,
                    onDeleteCandidate = viewModel::deleteCandidate,
                    onAccept = viewModel::acceptCandidate,
                    onStop = viewModel::stopGeneration,
                    onBackToPrompt = viewModel::hideCandidatePanel,
                    streamReasoning = if (activeGroup.taskType == TaskType.SUMMARY) summarySettings.streamThinking else bodySettings.streamThinking,
                    busy = busy,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    } else if (shortViewport && activeGroup != null && state.candidates.isNotEmpty() && candidatePanelExpanded) {
        CandidatePanel(
            group = activeGroup,
            candidates = state.candidates,
            onEdit = viewModel::editCandidate,
            onRoll = viewModel::roll,
            onDeleteCandidate = viewModel::deleteCandidate,
            onAccept = viewModel::acceptCandidate,
            onStop = viewModel::stopGeneration,
            onBackToPrompt = viewModel::hideCandidatePanel,
            streamReasoning = if (activeGroup.taskType == TaskType.SUMMARY) summarySettings.streamThinking else bodySettings.streamThinking,
            busy = busy,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ManuscriptPane(
                    state = state,
                    onContentChange = viewModel::updateChapterDraft,
                    onRename = viewModel::renameChapter,
                    onEditorFocusChange = { manuscriptFocused = it },
                    onOpenChapters = { focusManager.clearFocus(); keyboardController?.hide(); showChapters = true },
                    chaptersExpanded = showChapters,
                    onExportEpub = onExportEpub,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AnimatedVisibility(
                visible = (state.candidates.isEmpty() || !candidatePanelExpanded || activeGroup == null) && !hideDockForManuscriptKeyboard,
                enter = fadeIn(tween(160)) + slideInVertically { it / 3 },
                exit = fadeOut(tween(120)),
            ) {
                GenerationDock(
                    requirement = requirement,
                    onRequirementChange = { viewModel.creativeRequirement.value = it },
                    onInspect = viewModel::inspectBodyRequest,
                    busy = busy,
                    modelLabel = state.novel?.bodyModelId ?: "尚未配置模型",
                    failedGroup = state.activeGroups.firstOrNull { it.status == com.ainovel.local.data.model.CandidateGroupStatus.FAILED },
                    onRetry = viewModel::roll,
                    onDiscard = viewModel::discardGroup,
                    onStop = viewModel::stopGeneration,
                    onCreateNextChapter = viewModel::createNextChapterAndInspect,
                    pendingCandidateCount = state.candidates.size,
                    onShowCandidates = viewModel::showCandidatePanel,
                    onDeleteAllCandidates = { activeGroup?.let { viewModel.deleteAllCandidates(it.id) } },
                    compact = true,
                )
            }
            AnimatedVisibility(
                visible = activeGroup != null && state.candidates.isNotEmpty() && candidatePanelExpanded,
                enter = fadeIn(tween(180)) + slideInVertically { it / 3 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 4 },
            ) {
                if (activeGroup != null && state.candidates.isNotEmpty()) {
                    CandidatePanel(
                        group = activeGroup,
                        candidates = state.candidates,
                        onEdit = viewModel::editCandidate,
                        onRoll = viewModel::roll,
                        onDeleteCandidate = viewModel::deleteCandidate,
                        onAccept = viewModel::acceptCandidate,
                        onStop = viewModel::stopGeneration,
                        onBackToPrompt = viewModel::hideCandidatePanel,
                        streamReasoning = if (activeGroup.taskType == TaskType.SUMMARY) summarySettings.streamThinking else bodySettings.streamThinking,
                        busy = busy,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                    )
                }
            }
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            ChapterRail(
                state = state,
                deletedChapters = allChapters.filter { it.deleted },
                onSelect = {
                    viewModel.selectChapter(it)
                    showChapters = false
                },
                onAdd = {
                    viewModel.createNextChapter()
                    showChapters = false
                },
                onMove = viewModel::moveChapter,
                onDelete = viewModel::softDeleteChapter,
                onRestore = viewModel::restoreChapter,
                onPermanentDelete = viewModel::permanentlyDeleteChapter,
                onExportEpub = {
                    onExportEpub()
                    showChapters = false
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            )
        }
    }
}

@Composable
private fun ChapterRail(
    state: WorkspaceUiState,
    deletedChapters: List<ChapterEntity>,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onPermanentDelete: (String) -> Unit,
    onExportEpub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<ChapterEntity?>(null) }
    var permanentDeleteTarget by remember { mutableStateOf<ChapterEntity?>(null) }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("章节", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("故事顺序", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onExportEpub) { Icon(Icons.Outlined.Book, contentDescription = "导出 EPUB") }
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "创建下一章") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                val selected = chapter.id == state.selectedChapter?.id
                Surface(
                    onClick = { onSelect(chapter.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d".format(index + 1),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            chapter.name?.takeIf { it.isNotBlank() } ?: "未命名章节",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (chapter.includeInContext) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.CheckCircleOutline,
                                contentDescription = "已纳入上下文",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { onMove(chapter.id, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移章节", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onMove(chapter.id, 1) },
                            enabled = index < state.chapters.lastIndex,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移章节", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { deleteTarget = chapter },
                            enabled = state.chapters.size > 1,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "移入章节回收区", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (deletedChapters.isNotEmpty()) {
                item {
                    Text("章节回收区", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                itemsIndexed(deletedChapters, key = { _, chapter -> chapter.id }) { _, chapter ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                chapter.name?.ifBlank { null } ?: "未命名章节",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { onRestore(chapter.id) }) {
                                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "恢复章节")
                            }
                            IconButton(onClick = { permanentDeleteTarget = chapter }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "彻底删除章节",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("将章节移入回收区？") },
            text = { Text("章节会暂时收进回收区，正文、版本与创作记录都不会丢失。它将不再参与后续生成，依赖它的总结也会失效；恢复章节后，需要重新整理这些总结。") },
            confirmButton = { TextButton(onClick = { onDelete(chapter.id); deleteTarget = null }) { Text("移入回收区") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    permanentDeleteTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("彻底删除章节？") },
            text = { Text("彻底删除后，该章节的正文与版本将永久移除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPermanentDelete(chapter.id)
                        permanentDeleteTarget = null
                    },
                ) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { permanentDeleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ManuscriptPane(
    state: WorkspaceUiState,
    onContentChange: (String) -> Unit,
    onRename: (String, String?) -> Unit,
    onEditorFocusChange: (Boolean) -> Unit,
    onOpenChapters: (() -> Unit)?,
    chaptersExpanded: Boolean,
    onExportEpub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapter = state.selectedChapter
    var draft by remember(chapter?.id) { mutableStateOf(chapter?.content.orEmpty()) }
    var title by remember(chapter?.id) { mutableStateOf(chapter?.name.orEmpty()) }
    var chapterDetailsExpanded by rememberSaveable(chapter?.id) { mutableStateOf(false) }
    LaunchedEffect(chapter?.content) {
        if (chapter != null && chapter.content != draft) draft = chapter.content
    }
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onOpenChapters != null) {
                IconButton(onClick = onOpenChapters) {
                    Icon(
                        if (chaptersExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.UnfoldMore,
                        contentDescription = if (chaptersExpanded) "折叠章节列表" else "展开章节列表",
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "未命名章节" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "第 ${state.chapters.indexOfFirst { it.id == chapter?.id }.let { if (it >= 0) it + 1 else 1 }} 章 · 自动保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.latestUsage?.let { usage ->
                ContextUsageRing(usage)
            }
            IconButton(onClick = onExportEpub) {
                Icon(Icons.Outlined.Book, contentDescription = "导出 EPUB")
            }
            IconButton(onClick = { chapterDetailsExpanded = !chapterDetailsExpanded }) {
                Icon(
                    if (chapterDetailsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (chapterDetailsExpanded) "折叠章节信息" else "展开章节信息",
                )
            }
        }
        AnimatedVisibility(
            visible = chapterDetailsExpanded,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { -it / 5 },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("章节名（可以留空）") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { chapter?.let { onRename(it.id, title) } }) {
                            Icon(Icons.Outlined.EditNote, contentDescription = "保存章节名称")
                        }
                    },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill("自动保存", StatusTone.SAVED)
                    Text(
                        "聊天、思考过程和未采用的候选都不会混入正文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(if (chapterDetailsExpanded) 10.dp else 6.dp))
        if (chapter == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这里还没有可以书写的章节")
            }
        } else {
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onContentChange(it)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { onEditorFocusChange(it.isFocused) }
                    .verticalScroll(rememberScrollState())
                    .testTag("manuscript-editor")
                    .padding(bottom = 24.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            "从这里写下第一句话吧，你的每次修改都会悄悄保存。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun ContextUsageRing(usage: UsageRecordEntity) {
    var showDetails by rememberSaveable(usage.id) { mutableStateOf(false) }
    val rawRatio = if (usage.maxContextTokensSnapshot > 0) {
        usage.inputTokens.toFloat() / usage.maxContextTokensSnapshot.toFloat()
    } else {
        0f
    }
    val ratio = rawRatio.coerceIn(0f, 1f)
    val percent = rawRatio * 100f
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = when {
        rawRatio >= 0.9f -> MaterialTheme.colorScheme.error
        rawRatio >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val details = buildString {
        append("最近一次创作使用了 ${"%.2f".format(percent)}%，输入 ${"%,d".format(usage.inputTokens)} Token，可用上限 ${"%,d".format(usage.maxContextTokensSnapshot)} Token")
        if (usage.staleAgainstCurrentContent) append("。此后故事内容已经变化")
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClickLabel = "查看上下文使用量") { showDetails = true }
            .semantics {
                contentDescription = "上下文使用量"
                stateDescription = if (usage.staleAgainstCurrentContent) "${"%.1f".format(percent)}%，内容已变化" else "${"%.1f".format(percent)}%"
                progressBarRangeInfo = ProgressBarRangeInfo(ratio, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (ratio > 0f) {
                drawArc(
                    color = progress,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("最近一次上下文用量") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(details, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "圆环记录的是模型上一次实际收到的内容，不做猜测。故事或记忆发生变化后，旧结果仍会保留，并提醒你内容已有变化。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun GenerationDock(
    requirement: String,
    onRequirementChange: (String) -> Unit,
    onInspect: () -> Unit,
    busy: Boolean,
    modelLabel: String,
    modifier: Modifier = Modifier,
    failedGroup: CandidateGroupEntity? = null,
    onRetry: (String) -> Unit = {},
    onDiscard: (String) -> Unit = {},
    onStop: () -> Unit = {},
    onCreateNextChapter: () -> Unit = {},
    pendingCandidateCount: Int = 0,
    onShowCandidates: () -> Unit = {},
    onDeleteAllCandidates: () -> Unit = {},
    compact: Boolean = false,
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            if (pendingCandidateCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        onClick = onShowCandidates,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("$pendingCandidateCount 个候选待选择", modifier = Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
                            Text("查看", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除全部候选", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (failedGroup != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("这一次，灵感没有顺利抵达", style = MaterialTheme.typography.titleMedium)
                        Text("你的创作要求还在。再次尝试时，会沿用刚才确认过的故事记忆、模型和参数。", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryAction("按原内容重试", onClick = { onRetry(failedGroup.id) }, enabled = !busy, icon = Icons.Outlined.Refresh)
                            SecondaryAction("放弃", onClick = { onDiscard(failedGroup.id) }, enabled = !busy)
                        }
                    }
                }
            }
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = requirement,
                        onValueChange = onRequirementChange,
                        modifier = Modifier.weight(1f).testTag("creative-requirement-input"),
                        label = { Text("这一次想怎么写？") },
                        placeholder = { Text("写下续写方向") },
                        singleLine = true,
                        enabled = !busy && failedGroup == null,
                    )
                    FilledTonalIconButton(
                        onClick = onCreateNextChapter,
                        enabled = !busy && pendingCandidateCount == 0 && requirement.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "下一章并生成")
                    }
                    if (busy) {
                        FilledIconButton(onClick = onStop) {
                            Icon(Icons.Outlined.StopCircle, contentDescription = "停止生成")
                        }
                    } else {
                        FilledIconButton(
                            onClick = onInspect,
                            enabled = failedGroup == null && pendingCandidateCount == 0 && requirement.isNotBlank(),
                        ) {
                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = "检查并生成")
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = requirement,
                    onValueChange = onRequirementChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("这一次，你想让故事如何继续？") },
                    placeholder = { Text("例如：延续紧张氛围，让主角发现时间线的矛盾") },
                    maxLines = 3,
                    enabled = !busy && failedGroup == null,
                    supportingText = if (pendingCandidateCount > 0) {
                        { Text("可以先记下下一次的想法；处理完当前候选后再继续生成，这段草稿会一直保留。") }
                    } else {
                        null
                    },
                )
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stack = maxWidth < 430.dp
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(modelLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryAction("下一章", onClick = onCreateNextChapter, modifier = Modifier.weight(1f), enabled = !busy && pendingCandidateCount == 0 && requirement.isNotBlank(), icon = Icons.Outlined.Add)
                                if (busy) {
                                    SecondaryAction("停止生成", onClick = onStop, modifier = Modifier.weight(1f), icon = Icons.Outlined.StopCircle)
                                } else {
                                    PrimaryAction("检查并生成", onClick = onInspect, modifier = Modifier.weight(1f), enabled = failedGroup == null && pendingCandidateCount == 0 && requirement.isNotBlank(), icon = Icons.Outlined.AddCircleOutline)
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(modelLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                            SecondaryAction("下一章", onClick = onCreateNextChapter, enabled = !busy && pendingCandidateCount == 0 && requirement.isNotBlank(), icon = Icons.Outlined.Add)
                            if (busy) {
                                SecondaryAction("停止生成", onClick = onStop, icon = Icons.Outlined.StopCircle)
                            } else {
                                PrimaryAction("检查并生成", onClick = onInspect, enabled = failedGroup == null && pendingCandidateCount == 0 && requirement.isNotBlank(), icon = Icons.Outlined.AddCircleOutline)
                            }
                        }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("删除全部候选？") },
            text = { Text("这组的 $pendingCandidateCount 个候选会全部移除，连同对应的正文草稿与思考过程。已经写入的正文不会受影响，创作记录和用量信息仍会保留。") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; onDeleteAllCandidates() }) {
                    Text("全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidatePanel(
    group: CandidateGroupEntity,
    candidates: List<CandidateEntity>,
    onEdit: (String, String) -> Unit,
    onRoll: (String) -> Unit,
    onDeleteCandidate: (String) -> Unit,
    onAccept: (String) -> Unit,
    onStop: () -> Unit,
    onBackToPrompt: () -> Unit,
    streamReasoning: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { candidates.size })
    val scope = rememberCoroutineScope()
    var candidatePendingDelete by remember(group.id) { mutableStateOf<CandidateEntity?>(null) }
    val currentPage = pagerState.currentPage.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
    val displayPage = if (candidates.isEmpty()) 0 else currentPage + 1
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackToPrompt) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回创作要求")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (group.taskType == TaskType.SUMMARY) "总结候选" else "正文候选",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "候选 ${"%02d".format(displayPage)} · 还没有写入正文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((currentPage - 1).coerceAtLeast(0)) } },
                    enabled = currentPage > 0,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "上一个候选")
                }
                Text(
                    "$displayPage/${candidates.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((currentPage + 1).coerceAtMost(candidates.lastIndex.coerceAtLeast(0))) } },
                    enabled = currentPage < candidates.lastIndex,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "下一个候选")
                }
                if (busy) {
                    IconButton(onClick = onStop) { Icon(Icons.Outlined.StopCircle, contentDescription = "停止生成") }
                } else {
                    IconButton(onClick = { onRoll(group.id) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "用刚才确认的内容重新生成")
                    }
                }
                IconButton(
                    onClick = { candidates.getOrNull(currentPage)?.let { candidatePendingDelete = it } },
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除当前候选")
                }
            }
            HorizontalDivider()
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val candidate = candidates.getOrNull(page) ?: return@HorizontalPager
                var text by remember(candidate.id, candidate.editedText) { mutableStateOf(candidate.editedText) }
                var reasoningExpanded by rememberSaveable(candidate.id) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            if (candidate.complete) "生成完成" else "中途停下，仍可采用",
                            if (candidate.complete) StatusTone.SAVED else StatusTone.WARNING,
                        )
                        candidate.usageRecordId?.let { StatusPill("已记录实际用量", StatusTone.NEUTRAL) }
                    }
                    candidate.reasoningContent?.takeIf { it.isNotBlank() && (!busy || streamReasoning) }?.let { reasoning ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().animateContentSize(tween(220)),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClickLabel = if (reasoningExpanded) "折叠思考过程" else "展开思考过程") {
                                            reasoningExpanded = !reasoningExpanded
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("思考过程", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text("只供你查看，不会混入正文，也不会带到下一次创作中。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(
                                        if (reasoningExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = null,
                                    )
                                }
                                AnimatedVisibility(
                                    visible = reasoningExpanded,
                                    enter = fadeIn(tween(180)),
                                    exit = fadeOut(tween(120)),
                                ) {
                                    Text(
                                        reasoning,
                                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } ?: Text(
                        when {
                            busy && candidate.reasoningKind != com.ainovel.local.data.model.ReasoningKind.NONE && !streamReasoning -> "正在梳理思路，完成后可以展开查看。"
                            candidate.reasoningKind == com.ainovel.local.data.model.ReasoningKind.NONE -> "这一次没有开启思考，或当前模型不支持展示思考过程。"
                            else -> "这一次没有留下可展示的思考过程。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onEdit(candidate.id, it)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        enabled = !busy,
                    )
                    PrimaryAction(
                        text = if (group.taskType == TaskType.SUMMARY) "接受总结" else "接受并追加",
                        onClick = { onAccept(candidate.id) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = text.isNotBlank() && !busy,
                        icon = Icons.Outlined.CheckCircleOutline,
                    )
                }
            }
        }
    }
    candidatePendingDelete?.let { candidate ->
        AlertDialog(
            onDismissRequest = { candidatePendingDelete = null },
            title = { Text("删除当前候选？") },
            text = { Text("候选 ${"%02d".format(candidate.rollNumber)} 的正文草稿与思考过程会被移除。已经写入的正文不会受影响，创作记录和用量信息仍会保留。") },
            confirmButton = {
                TextButton(onClick = { candidatePendingDelete = null; onDeleteCandidate(candidate.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { candidatePendingDelete = null }) { Text("取消") } },
        )
    }
}
