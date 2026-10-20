package com.ainovel.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import com.ainovel.local.ui.localization.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.ContextNodeType
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.Ids
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.SummaryStatus
import com.ainovel.local.data.model.SummaryDensity
import com.ainovel.local.ui.AppViewModel
import com.ainovel.local.ui.components.PrimaryAction
import com.ainovel.local.ui.components.SectionHeader
import com.ainovel.local.ui.components.StatusPill
import com.ainovel.local.ui.components.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextScreen(viewModel: AppViewModel) {
    val state by viewModel.workspace.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val worldbook by viewModel.worldbook.collectAsStateWithLifecycle()
    val globalPresets by viewModel.globalPresets.collectAsStateWithLifecycle()
    val globalWorldbook by viewModel.globalWorldbook.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<ContextEditorState?>(null) }
    var chapterEditor by remember { mutableStateOf<ChapterEntity?>(null) }
    var deleteChapterTarget by remember { mutableStateOf<ChapterEntity?>(null) }
    var summaryEditor by remember { mutableStateOf<SummaryEntity?>(null) }
    var deleteSummaryTarget by remember { mutableStateOf<SummaryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ContextEntryEntity?>(null) }
    var showSummaryComposer by remember { mutableStateOf(false) }
    val tabs = listOf("记忆", "预设", "世界书", "灵感素材")

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> NodeList(
                chapters = state.chapters,
                summaries = state.summaries,
                onToggleChapter = viewModel::toggleChapterContext,
                onEditChapter = { chapterEditor = it },
                onDeleteChapter = { deleteChapterTarget = it },
                onToggleSummary = viewModel::toggleSummaryContext,
                onEditSummary = { summaryEditor = it },
                onDeleteSummary = { deleteSummaryTarget = it },
                onGenerateSummary = {
                    viewModel.loadDefaultSummarySources()
                    showSummaryComposer = true
                },
                modifier = Modifier.weight(1f),
            )
            1 -> EntryList(
                title = "本作预设",
                description = "这些约定会排在世界书之前，按你安排的顺序陪模型理解故事。名称只在这里可见。",
                entries = presets,
                onAdd = { editor = ContextEditorState(type = EntryType.PRESET, scope = EntryScope.NOVEL) },
                onEdit = { editor = ContextEditorState(it) },
                onDelete = { deleteTarget = it },
                onMove = viewModel::swapContextEntries,
                onCopyToGlobal = viewModel::copyContextEntryToGlobal,
                modifier = Modifier.weight(1f),
            )
            2 -> EntryList(
                title = "本作世界书",
                description = "这里收纳故事的世界规则，启用后会完整陪伴每一次创作。",
                entries = worldbook,
                onAdd = { editor = ContextEditorState(type = EntryType.WORLDBOOK, scope = EntryScope.NOVEL) },
                onEdit = { editor = ContextEditorState(it) },
                onDelete = { deleteTarget = it },
                onMove = viewModel::swapContextEntries,
                onCopyToGlobal = viewModel::copyContextEntryToGlobal,
                modifier = Modifier.weight(1f),
            )
            else -> GlobalLibrary(
                presets = globalPresets,
                worldbook = globalWorldbook,
                onAdd = { type -> editor = ContextEditorState(type = type, scope = EntryScope.GLOBAL) },
                onEdit = { editor = ContextEditorState(it) },
                onDelete = { deleteTarget = it },
                onCopy = viewModel::copyContextEntryToNovel,
                modifier = Modifier.weight(1f),
            )
        }
    }

    editor?.let { value ->
        ContextEntryDialog(
            initial = value,
            novelId = state.novel?.id,
            onDismiss = { editor = null },
            onSave = {
                viewModel.addContextEntry(it)
                editor = null
            },
        )
    }
    chapterEditor?.let { chapter ->
        ChapterTitleEditDialog(
            chapter = chapter,
            onDismiss = { chapterEditor = null },
            onSave = { newName ->
                viewModel.renameChapter(chapter.id, newName)
                chapterEditor = null
            },
        )
    }
    deleteChapterTarget?.let { chapter ->
        AlertDialog(
            onDismissRequest = { deleteChapterTarget = null },
            title = { Text("将章节移入回收区？") },
            text = { Text("章节会暂时收进回收区，正文、版本与创作记录都不会丢失。它将不再参与后续生成，依赖它的总结也会失效；恢复章节后，需要重新整理这些总结。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.softDeleteChapter(chapter.id)
                        deleteChapterTarget = null
                    },
                ) { Text("移入回收区") }
            },
            dismissButton = { TextButton(onClick = { deleteChapterTarget = null }) { Text("取消") } },
        )
    }
    summaryEditor?.let { summary ->
        SummaryEditDialog(
            summary = summary,
            onDismiss = { summaryEditor = null },
            onSave = { name, text ->
                viewModel.updateSummary(summary.id, name, text)
                summaryEditor = null
            },
        )
    }
    deleteSummaryTarget?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleteSummaryTarget = null },
            title = { Text("删除“${summary.name}”？") },
            text = {
                Text("删除后，这份总结会从故事记忆中移除；依赖它的后续总结会失效，过去的创作记录仍会保留。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSummary(summary.id)
                        deleteSummaryTarget = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteSummaryTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除“${entry.name}”？") },
            text = {
                Text(
                    if (entry.scope == EntryScope.NOVEL) "这条内容会从当前作品中移除，过去的创作记录仍会原样保留。"
                    else "这条素材会从素材库中删除；已经复制到各部作品里的内容不会受到影响。",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteContextEntry(entry); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (showSummaryComposer) {
        val selections by viewModel.summarySourceSelections.collectAsStateWithLifecycle()
        val density by viewModel.summaryDensity.collectAsStateWithLifecycle()
        SummarySourceDialog(
            chapters = state.chapters,
            summaries = state.summaries.filter { it.status != SummaryStatus.INVALID },
            selections = selections,
            density = density,
            onToggle = viewModel::toggleSummarySource,
            onDensityChange = { viewModel.summaryDensity.value = it },
            onUseDefaults = viewModel::loadDefaultSummarySources,
            onDismiss = { showSummaryComposer = false },
            onContinue = {
                viewModel.inspectSelectedSummaryRequest()
                showSummaryComposer = false
            },
        )
    }
}

@Composable
private fun NodeList(
    chapters: List<ChapterEntity>,
    summaries: List<SummaryEntity>,
    onToggleChapter: (ChapterEntity) -> Unit,
    onEditChapter: (ChapterEntity) -> Unit,
    onDeleteChapter: (ChapterEntity) -> Unit,
    onToggleSummary: (SummaryEntity) -> Unit,
    onEditSummary: (SummaryEntity) -> Unit,
    onDeleteSummary: (SummaryEntity) -> Unit,
    onGenerateSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coveredIds = remember(summaries) {
        summaries.filter { it.includeInContext && it.status != SummaryStatus.INVALID }
            .flatMap { summary -> Regex("[0-9a-fA-F-]{36}").findAll(summary.coveredChapterIdsJson).map { it.value }.toList() }
            .toSet()
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                title = "故事记忆",
                description = "你勾选的正文与仍然有效的总结，才会陪伴下一次创作。接受新总结后，它的直接来源会自动退出，避免重复。",
                trailing = {
                    PrimaryAction("整理故事总结", onClick = onGenerateSummary, icon = Icons.Outlined.Summarize)
                },
            )
        }
        if (chapters.isEmpty() && summaries.isEmpty()) {
            item { EmptyPanel("这里还没有被选中的故事记忆") }
        }
        itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
            val overlap = chapter.includeInContext && chapter.id in coveredIds
            val title = chapter.name?.ifBlank { null } ?: "未命名章节"
            NodeRow(
                index = index + 1,
                title = title,
                kind = "正文",
                excerpt = chapter.content,
                included = chapter.includeInContext,
                enabled = true,
                warning = if (overlap) "这段内容已经包含在所选总结中" else null,
                onToggle = { onToggleChapter(chapter) },
                onEdit = { onEditChapter(chapter) },
                onDelete = if (chapters.size > 1) ({ onDeleteChapter(chapter) }) else null,
            )
        }
        items(summaries, key = { it.id }) { summary ->
            NodeRow(
                index = null,
                title = summary.name,
                kind = "总结 · ${summaryStatus(summary.status)}",
                excerpt = summary.content,
                included = summary.includeInContext,
                enabled = summary.status != SummaryStatus.INVALID,
                warning = when (summary.status) {
                    SummaryStatus.STALE -> "故事已经变化，建议重新整理"
                    SummaryStatus.INVALID -> "来源已经变化，暂时不会参与创作"
                    else -> null
                },
                onToggle = { onToggleSummary(summary) },
                onEdit = { onEditSummary(summary) },
                onDelete = { onDeleteSummary(summary) },
            )
        }
    }
}

@Composable
private fun SummarySourceDialog(
    chapters: List<ChapterEntity>,
    summaries: List<SummaryEntity>,
    selections: List<com.ainovel.local.domain.SummarySourceSelection>,
    density: SummaryDensity,
    onToggle: (String, ContextNodeType) -> Unit,
    onDensityChange: (SummaryDensity) -> Unit,
    onUseDefaults: () -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val selectedKeys = remember(selections) { selections.mapTo(mutableSetOf()) { it.nodeId to it.nodeType } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择想要凝练的内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("只会整理你在这里选中的章节和总结，其余内容不会被带入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryDensity.entries.forEach { option ->
                        FilterChip(
                            selected = density == option,
                            onClick = { onDensityChange(option) },
                            label = { Text(when (option) { SummaryDensity.CONCISE -> "简洁"; SummaryDensity.STANDARD -> "标准"; SummaryDensity.DETAILED -> "详细" }) },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(chapters, key = { _, it -> it.id }) { index, chapter ->
                        SummarySourceRow(
                            checked = chapter.id to ContextNodeType.CHAPTER in selectedKeys,
                            title = "第${index + 1}章 · ${chapter.name?.ifBlank { null } ?: "未命名"}",
                            subtitle = chapter.content.ifBlank { "暂无正文" },
                            onClick = { onToggle(chapter.id, ContextNodeType.CHAPTER) },
                        )
                    }
                    items(summaries, key = { it.id }) { summary ->
                        SummarySourceRow(
                            checked = summary.id to ContextNodeType.SUMMARY in selectedKeys,
                            title = summary.name,
                            subtitle = "已有总结 · ${summaryStatus(summary.status)}",
                            onClick = { onToggle(summary.id, ContextNodeType.SUMMARY) },
                        )
                    }
                }
                TextButton(onClick = onUseDefaults) { Text("恢复推荐选择") }
            }
        },
        confirmButton = { TextButton(enabled = selections.isNotEmpty(), onClick = onContinue) { Text("发送前检查") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SummarySourceRow(checked: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NodeRow(
    index: Int?,
    title: String,
    kind: String,
    excerpt: String,
    included: Boolean,
    enabled: Boolean,
    warning: String?,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = included,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = included, onCheckedChange = null, enabled = enabled)
            if (index != null) {
                Text("%02d".format(index), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                Text(
                    excerpt.ifBlank { "（暂无内容）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                warning?.let { StatusPill(it, StatusTone.WARNING) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                onEdit?.let {
                    IconButton(onClick = it) { Icon(Icons.Outlined.EditNote, contentDescription = "编辑 $title") }
                }
                onDelete?.let {
                    IconButton(onClick = it) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除 $title") }
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    title: String,
    description: String,
    entries: List<ContextEntryEntity>,
    onAdd: () -> Unit,
    onEdit: (ContextEntryEntity) -> Unit,
    onDelete: (ContextEntryEntity) -> Unit,
    onMove: (String, String) -> Unit,
    onCopyToGlobal: (ContextEntryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader(title, description = description, trailing = { PrimaryAction("新建", onClick = onAdd, icon = Icons.Outlined.Add) }) }
        if (entries.isEmpty()) item { EmptyPanel("还没有条目") }
        itemsIndexed(entries, key = { _, item -> item.id }) { index, entry ->
            EntryCard(
                entry = entry,
                onEdit = { onEdit(entry) },
                onDelete = { onDelete(entry) },
                onMoveUp = if (index > 0) {{ onMove(entry.id, entries[index - 1].id) }} else null,
                onMoveDown = if (index < entries.lastIndex) {{ onMove(entry.id, entries[index + 1].id) }} else null,
                onCopy = { onCopyToGlobal(entry) },
            )
        }
    }
}

@Composable
private fun GlobalLibrary(
    presets: List<ContextEntryEntity>,
    worldbook: List<ContextEntryEntity>,
    onAdd: (EntryType) -> Unit,
    onEdit: (ContextEntryEntity) -> Unit,
    onDelete: (ContextEntryEntity) -> Unit,
    onCopy: (ContextEntryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by remember { mutableStateOf(EntryType.PRESET) }
    val entries = if (type == EntryType.PRESET) presets else worldbook
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeader(
                "灵感素材库",
                description = "这里像一只安静的抽屉。只有复制到当前作品并主动启用后，素材才会参与创作。",
                trailing = { PrimaryAction("新建", onClick = { onAdd(type) }, icon = Icons.Outlined.Add) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == EntryType.PRESET, onClick = { type = EntryType.PRESET }, label = { Text("预设") })
                FilterChip(selected = type == EntryType.WORLDBOOK, onClick = { type = EntryType.WORLDBOOK }, label = { Text("世界书") })
            }
        }
        if (entries.isEmpty()) item { EmptyPanel("这里还没有这类素材") }
        items(entries, key = { it.id }) { entry ->
            EntryCard(
                entry = entry,
                onEdit = { onEdit(entry) },
                onDelete = { onDelete(entry) },
                onCopy = { onCopy(entry) },
            )
        }
    }
}

@Composable
private fun EntryCard(
    entry: ContextEntryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusPill(if (entry.scope == EntryScope.GLOBAL) "珍藏于素材库" else if (entry.enabled) "已陪伴创作" else "暂不启用", if (entry.enabled) StatusTone.SAVED else StatusTone.NEUTRAL)
                }
                Text(entry.content, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "${entry.name}的更多操作")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    onMoveUp?.let { action ->
                        EntryAction("上移", Icons.Outlined.ArrowUpward) { menuExpanded = false; action() }
                    }
                    onMoveDown?.let { action ->
                        EntryAction("下移", Icons.Outlined.ArrowDownward) { menuExpanded = false; action() }
                    }
                    onCopy?.let { action ->
                        EntryAction(if (entry.scope == EntryScope.GLOBAL) "复制到本作" else "复制到全局库", Icons.Outlined.ContentCopy) {
                            menuExpanded = false
                            action()
                        }
                    }
                    EntryAction("编辑", Icons.Outlined.EditNote) { menuExpanded = false; onEdit() }
                    HorizontalDivider()
                    EntryAction("删除", Icons.Outlined.DeleteOutline) { menuExpanded = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun EntryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

private data class ContextEditorState(
    val id: String = Ids.newId(),
    val type: EntryType,
    val scope: EntryScope,
    val name: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    val sortOrder: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    constructor(entry: ContextEntryEntity) : this(entry.id, entry.type, entry.scope, entry.name, entry.content, entry.enabled, entry.sortOrder, entry.createdAt)
}

@Composable
private fun ContextEntryDialog(
    initial: ContextEditorState,
    novelId: String?,
    onDismiss: () -> Unit,
    onSave: (ContextEntryEntity) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var content by remember(initial.id) { mutableStateOf(initial.content) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "新建${entryTypeLabel(initial.type)}" else "编辑${entryTypeLabel(initial.type)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("给自己看的名字") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("会交给模型的内容") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), minLines = 6)
                if (initial.scope == EntryScope.NOVEL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Text(if (enabled) "让它陪伴本作创作" else "先收好，暂不启用", modifier = Modifier.padding(start = 10.dp))
                    }
                } else {
                    Text("素材库里的内容不会自行进入任何作品。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && content.isNotBlank(),
                onClick = {
                    onSave(
                        ContextEntryEntity(
                            id = initial.id,
                            type = initial.type,
                            scope = initial.scope,
                            novelId = if (initial.scope == EntryScope.NOVEL) novelId else null,
                            name = name.trim(),
                            content = content.trim(),
                            enabled = if (initial.scope == EntryScope.GLOBAL) false else enabled,
                            sortOrder = initial.sortOrder,
                            createdAt = initial.createdAt,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChapterTitleEditDialog(
    chapter: ChapterEntity,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var name by remember(chapter.id, chapter.name) { mutableStateOf(chapter.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑章节标题") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("章节名（可以留空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifBlank { null }) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SummaryEditDialog(
    summary: SummaryEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(summary.id, summary.name) { mutableStateOf(summary.name) }
    var text by remember(summary.id, summary.content) { mutableStateOf(summary.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑${summary.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("修改这份总结后，依赖它的后续总结会提醒你重新整理。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("总结标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("总结正文") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(name.trim(), text.trim()) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EmptyPanel(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun entryTypeLabel(type: EntryType) = if (type == EntryType.PRESET) "预设" else "世界书"
private fun summaryStatus(status: SummaryStatus) = when (status) {
    SummaryStatus.VALID -> "可用"
    SummaryStatus.STALE -> "待更新"
    SummaryStatus.INVALID -> "已失效"
}
