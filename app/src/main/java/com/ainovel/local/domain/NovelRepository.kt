package com.ainovel.local.domain

import androidx.room.withTransaction
import com.ainovel.local.data.local.GenerationDao
import com.ainovel.local.data.local.ModelDao
import com.ainovel.local.data.local.NovelDao
import com.ainovel.local.data.local.NovelDatabase
import com.ainovel.local.data.model.AcceptanceRecordEntity
import com.ainovel.local.data.model.AssistantSessionEntity
import com.ainovel.local.data.model.AppSettingsEntity
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.ChapterRevisionEntity
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.ContextNodeType
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.Ids
import com.ainovel.local.data.model.ModelSettingEntity
import com.ainovel.local.data.model.NovelEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.RevisionType
import com.ainovel.local.data.model.SummaryDensity
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.SummarySourceEntity
import com.ainovel.local.data.model.SummaryStatus
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface UndoAcceptResult {
    data object Success : UndoAcceptResult
    data object AlreadyUndone : UndoAcceptResult
    data class Conflict(val currentVersion: Long, val expectedVersion: Long) : UndoAcceptResult
    data object NotFound : UndoAcceptResult
}

data class HistoryCleanupPreview(
    val matchedRequests: Int,
    val deletableRequests: Int,
    val blockedRequests: Int,
    val candidateCount: Int,
    val usageCount: Int,
) {
    val traceabilityNote: String
        get() = if (blockedRequests == 0) {
            "只会清理不再被正文、总结或版本引用的记录，现有内容不会改变。"
        } else {
            "其中有 $blockedRequests 条仍与候选、正文或版本相连，已经为你保留，不会影响任何创作内容。"
        }
}

data class HistoryCleanupResult(
    val deletedRequests: Int,
    val deletedCandidates: Int,
    val deletedUsage: Int,
    val blockedRequests: Int,
)

data class CandidateDeletionResult(
    val deletedCount: Int,
    val remainingCount: Int,
    val groupDiscarded: Boolean,
)

data class PreparedSummaryUpdate(
    val updatedSummary: SummaryEntity,
    val nameChanged: Boolean,
    val contentChanged: Boolean,
)

fun prepareSummaryUpdate(
    existing: SummaryEntity,
    newName: String,
    newContent: String,
    now: Long = System.currentTimeMillis(),
): PreparedSummaryUpdate {
    val trimmedName = newName.trim()
    val trimmedContent = newContent.trim()
    require(trimmedName.isNotEmpty()) { "总结标题不能为空" }
    require(trimmedContent.isNotEmpty()) { "总结内容不能为空" }
    val nameChanged = existing.name != trimmedName
    val contentChanged = existing.content != trimmedContent
    val updated = existing.copy(
        name = trimmedName,
        content = trimmedContent,
        contentVersion = if (contentChanged) existing.contentVersion + 1 else existing.contentVersion,
        updatedAt = if (nameChanged || contentChanged) now else existing.updatedAt,
    )
    return PreparedSummaryUpdate(
        updatedSummary = updated,
        nameChanged = nameChanged,
        contentChanged = contentChanged,
    )
}

fun preparePermanentChapterDeletion(chapter: ChapterEntity?): ChapterEntity {
    val existing = chapter ?: error("章节不存在")
    require(existing.deleted) { "只能彻底删除回收区中的章节" }
    return existing
}

class NovelRepository(
    private val database: NovelDatabase,
    private val novelDao: NovelDao,
    private val generationDao: GenerationDao,
    private val modelDao: ModelDao,
    private val json: Json,
) {
    fun observeNovels(): Flow<List<NovelEntity>> = novelDao.observeNovels()
    fun observeNovel(id: String): Flow<NovelEntity?> = novelDao.observeNovel(id)
    fun observeChapters(novelId: String): Flow<List<ChapterEntity>> = novelDao.observeActiveChapters(novelId)
    fun observeAllChapters(novelId: String): Flow<List<ChapterEntity>> = novelDao.observeAllChapters(novelId)
    fun observeAppSettings(): Flow<AppSettingsEntity?> = novelDao.observeAppSettings()
    fun observeSummaries(novelId: String): Flow<List<SummaryEntity>> = novelDao.observeSummaries(novelId)
    fun observeContextEntries(scope: EntryScope, type: com.ainovel.local.data.model.EntryType, novelId: String?) =
        novelDao.observeContextEntries(scope, type, novelId)
    fun observeProviders(): Flow<List<ProviderConfigEntity>> = novelDao.observeProviders()
    fun observeModels(providerId: String): Flow<List<ModelSettingEntity>> = modelDao.observeModels(providerId)
    fun observeAllModels(): Flow<List<ModelSettingEntity>> = modelDao.observeAllModels()
    fun observeActiveGroups(novelId: String) = generationDao.observeActiveGroups(novelId)
    fun observeCandidates(groupId: String) = generationDao.observeCandidates(groupId)
    fun observeHistory(novelId: String) = generationDao.observeRequestSnapshots(novelId)
    fun observeHistoryCandidates(novelId: String) = generationDao.observeNovelCandidates(novelId)
    fun observeHistoryUsage(novelId: String) = generationDao.observeNovelUsage(novelId)
    fun observeLatestUsage(novelId: String, taskType: TaskType) = generationDao.observeLatestUsage(novelId, taskType)
    fun observeAssistantSession(novelId: String) = novelDao.observeAssistantSession(novelId)
    fun observeAcceptances(novelId: String) = generationDao.observeAcceptances(novelId)

    suspend fun novel(id: String) = novelDao.novel(id)
    suspend fun chapter(id: String) = novelDao.chapter(id)
    suspend fun allChapters(novelId: String): List<ChapterEntity> = novelDao.allChapters(novelId)
    suspend fun candidateGroup(id: String) = generationDao.candidateGroup(id)
    suspend fun candidate(id: String) = generationDao.candidate(id)
    suspend fun requestSnapshot(id: String) = generationDao.requestSnapshot(id)
    suspend fun provider(id: String) = novelDao.provider(id)
    suspend fun model(providerId: String, modelId: String) = modelDao.model(providerId, modelId)
    suspend fun models(providerId: String) = modelDao.models(providerId)

    suspend fun updateNovelCover(novelId: String, coverFileName: String?) {
        check(novelDao.updateNovelCover(novelId, coverFileName) == 1) { "小说不存在" }
    }

    suspend fun updateGenerationSettings(
        novelId: String,
        body: GenerationSettings,
        summary: GenerationSettings,
        assistant: GenerationSettings,
    ) {
        check(
            novelDao.updateGenerationSettings(
                novelId = novelId,
                bodyJson = json.encodeToString(body),
                summaryJson = json.encodeToString(summary),
                assistantJson = json.encodeToString(assistant),
            ) == 1,
        )
    }

    suspend fun previewHistoryCleanup(
        novelId: String,
        taskType: TaskType?,
        olderThan: Long?,
    ): HistoryCleanupPreview {
        val matches = generationDao.allRequestSnapshots().filter {
            it.novelId == novelId &&
                (taskType == null || it.taskType == taskType) &&
                (olderThan == null || it.requestedAt < olderThan)
        }
        var deletable = 0
        var candidates = 0
        var usages = 0
        val usageByRequest = generationDao.allUsage().associateBy { it.requestSnapshotId }
        val matchIds = matches.mapTo(mutableSetOf()) { it.id }
        val handled = mutableSetOf<String>()
        matches.forEach { snapshot ->
            if (!handled.add(snapshot.id)) return@forEach
            val groupId = snapshot.candidateGroupId
            val group = if (groupId != null) generationDao.candidateGroup(groupId) else null
            if (group != null) {
                val groupSnapshots = generationDao.snapshotsForGroup(group.id)
                val groupSnapshotIds = groupSnapshots.mapTo(mutableSetOf()) { it.id }
                val groupCandidates = generationDao.candidates(group.id)
                val safeGroup = group.status == CandidateGroupStatus.DISCARDED &&
                    groupSnapshotIds.all { it in matchIds } &&
                    groupCandidates.none { candidate ->
                        generationDao.acceptanceReferences(candidate.id) > 0 ||
                            generationDao.revisionReferences(candidate.id) > 0
                    }
                if (safeGroup) {
                    handled += groupSnapshotIds
                    deletable += groupSnapshots.size
                    candidates += groupCandidates.size
                    usages += groupSnapshots.count { it.id in usageByRequest }
                }
            } else if (generationDao.candidateReferences(snapshot.id) == 0) {
                deletable++
                if (snapshot.id in usageByRequest) usages++
            }
        }
        return HistoryCleanupPreview(matches.size, deletable, matches.size - deletable, candidates, usages)
    }

    suspend fun cleanupHistory(
        novelId: String,
        taskType: TaskType?,
        olderThan: Long?,
    ): HistoryCleanupResult = database.withTransaction {
        val matches = generationDao.allRequestSnapshots().filter {
            it.novelId == novelId &&
                (taskType == null || it.taskType == taskType) &&
                (olderThan == null || it.requestedAt < olderThan)
        }
        var deletedRequests = 0
        var deletedCandidates = 0
        var deletedUsage = 0
        var blocked = 0
        val matchIds = matches.mapTo(mutableSetOf()) { it.id }
        val handled = mutableSetOf<String>()
        matches.forEach { snapshot ->
            if (!handled.add(snapshot.id)) return@forEach
            val groupId = snapshot.candidateGroupId
            val group = if (groupId != null) generationDao.candidateGroup(groupId) else null
            if (group != null) {
                val groupSnapshots = generationDao.snapshotsForGroup(group.id)
                val groupSnapshotIds = groupSnapshots.mapTo(mutableSetOf()) { it.id }
                val groupCandidates = generationDao.candidates(group.id)
                val safeGroup = group.status == CandidateGroupStatus.DISCARDED &&
                    groupSnapshotIds.all { it in matchIds } &&
                    groupCandidates.none { candidate ->
                        generationDao.acceptanceReferences(candidate.id) > 0 ||
                            generationDao.revisionReferences(candidate.id) > 0
                    }
                if (safeGroup) {
                    handled += groupSnapshotIds
                    groupCandidates.forEach { deletedCandidates += generationDao.deleteCandidate(it.id) }
                    groupSnapshots.forEach { deletedUsage += generationDao.deleteUsageForRequest(it.id) }
                    generationDao.deleteCandidateGroup(group.id)
                    groupSnapshots.forEach { deletedRequests += generationDao.deleteRequestSnapshot(it.id) }
                } else {
                    blocked += groupSnapshotIds.count { it in matchIds && it !in handled } + 1
                    handled += groupSnapshotIds
                }
            } else if (generationDao.candidateReferences(snapshot.id) == 0) {
                deletedUsage += generationDao.deleteUsageForRequest(snapshot.id)
                deletedRequests += generationDao.deleteRequestSnapshot(snapshot.id)
            } else {
                blocked++
            }
        }
        HistoryCleanupResult(deletedRequests, deletedCandidates, deletedUsage, blocked)
    }

    suspend fun createNovel(name: String): NovelEntity = database.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "请输入小说名称" }
        var settings = novelDao.appSettings()
        if (settings == null) {
            settings = AppSettingsEntity()
            novelDao.upsertAppSettings(settings)
        }
        val novel = NovelEntity(name = trimmed, summaryInstruction = settings.defaultSummaryInstruction)
        novelDao.insertNovel(novel)
        novelDao.insertChapter(
            ChapterEntity(
                novelId = novel.id,
                storyOrder = ORDER_STEP,
            ),
        )
        novelDao.upsertAssistantSession(AssistantSessionEntity(novelId = novel.id))
        novel
    }

    suspend fun createNextChapter(novelId: String): ChapterEntity = database.withTransaction {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        val chapter = ChapterEntity(
            novelId = novelId,
            storyOrder = novelDao.maxChapterOrder(novelId) + ORDER_STEP,
            includeInContext = true,
        )
        novelDao.insertChapter(chapter)
        novelDao.touchNovel(novelId)
        chapter
    }

    suspend fun deleteEmptyChapter(chapterId: String) {
        novelDao.deletePristineChapter(chapterId)
    }

    suspend fun saveChapterContent(chapterId: String, content: String) = database.withTransaction {
        val chapter = novelDao.chapter(chapterId) ?: error("章节不存在")
        if (chapter.content == content) return@withTransaction
        check(novelDao.updateChapterContent(chapterId, content) == 1)
        generationDao.markUsageStale(chapter.novelId, TaskType.BODY)
        generationDao.markUsageStale(chapter.novelId, TaskType.ASSISTANT)
        markDependentSummaries(chapterId, SummaryStatus.STALE)
        novelDao.touchNovel(chapter.novelId)
    }

    suspend fun renameChapter(chapterId: String, name: String?) {
        check(novelDao.renameChapter(chapterId, name?.trim()?.ifBlank { null }) == 1)
    }

    suspend fun setChapterIncluded(chapterId: String, included: Boolean) = database.withTransaction {
        val chapter = novelDao.chapter(chapterId) ?: error("章节不存在")
        check(novelDao.setChapterIncluded(chapterId, included) == 1)
        markBaseContextUsageStale(chapter.novelId)
    }

    suspend fun softDeleteChapter(chapterId: String) = database.withTransaction {
        val chapter = novelDao.chapter(chapterId) ?: error("章节不存在")
        check(novelDao.softDeleteChapter(chapterId) == 1)
        markDependentSummaries(chapterId, SummaryStatus.INVALID)
        markBaseContextUsageStale(chapter.novelId)
        novelDao.touchNovel(chapter.novelId)
    }

    suspend fun restoreChapter(chapterId: String) = database.withTransaction {
        val chapter = novelDao.chapter(chapterId) ?: error("章节不存在")
        check(novelDao.restoreChapter(chapterId) == 1)
        markBaseContextUsageStale(chapter.novelId)
    }

    suspend fun permanentlyDeleteChapter(chapterId: String) = database.withTransaction {
        val chapter = preparePermanentChapterDeletion(novelDao.chapter(chapterId))
        markDependentSummaries(chapter.id, SummaryStatus.INVALID)
        generationDao.deleteAcceptancesForChapter(chapter.id)
        check(novelDao.permanentlyDeleteChapter(chapter.id) == 1)
        markBaseContextUsageStale(chapter.novelId)
        novelDao.touchNovel(chapter.novelId)
    }

    suspend fun reorderChapter(chapterId: String, newStoryOrder: Long) = database.withTransaction {
        require(newStoryOrder > 0) { "章节顺序必须大于零" }
        val chapter = novelDao.chapter(chapterId) ?: error("章节不存在")
        if (chapter.storyOrder == newStoryOrder) return@withTransaction
        check(novelDao.setChapterOrder(chapterId, newStoryOrder) == 1)
        markDependentSummaries(chapterId, SummaryStatus.INVALID)
        markBaseContextUsageStale(chapter.novelId)
        novelDao.touchNovel(chapter.novelId)
    }

    suspend fun swapChapterOrder(firstId: String, secondId: String) = database.withTransaction {
        val first = novelDao.chapter(firstId) ?: error("章节不存在")
        val second = novelDao.chapter(secondId) ?: error("章节不存在")
        require(first.novelId == second.novelId && !first.deleted && !second.deleted) { "只能调整同一小说的有效章节" }
        if (first.storyOrder == second.storyOrder) return@withTransaction
        val temporary = -System.currentTimeMillis()
        check(novelDao.setChapterOrder(first.id, temporary) == 1)
        check(novelDao.setChapterOrder(second.id, first.storyOrder) == 1)
        check(novelDao.setChapterOrder(first.id, second.storyOrder) == 1)
        markDependentSummaries(first.id, SummaryStatus.INVALID)
        markDependentSummaries(second.id, SummaryStatus.INVALID)
        markBaseContextUsageStale(first.novelId)
        novelDao.touchNovel(first.novelId)
    }

    suspend fun upsertContextEntry(entry: ContextEntryEntity) = database.withTransaction {
        require(entry.name.isNotBlank()) { "请输入管理名称" }
        require(entry.content.isNotBlank()) { "请输入内容" }
        if (entry.scope == EntryScope.NOVEL) requireNotNull(entry.novelId)
        val existing = novelDao.contextEntry(entry.id)
        if (existing == null) novelDao.insertContextEntry(entry) else novelDao.updateContextEntry(entry)
        entry.novelId?.let { markBaseContextUsageStale(it) }
    }

    suspend fun deleteContextEntry(entry: ContextEntryEntity) = database.withTransaction {
        novelDao.deleteContextEntry(entry)
        entry.novelId?.let { markBaseContextUsageStale(it) }
    }

    suspend fun swapContextEntries(firstId: String, secondId: String) = database.withTransaction {
        val first = novelDao.contextEntry(firstId) ?: error("条目不存在")
        val second = novelDao.contextEntry(secondId) ?: error("条目不存在")
        require(first.scope == second.scope && first.type == second.type && first.novelId == second.novelId) {
            "只能调整同一分组中的顺序"
        }
        novelDao.updateContextEntry(first.copy(sortOrder = second.sortOrder, updatedAt = System.currentTimeMillis()))
        novelDao.updateContextEntry(second.copy(sortOrder = first.sortOrder, updatedAt = System.currentTimeMillis()))
        first.novelId?.let { markBaseContextUsageStale(it) }
    }

    suspend fun setSummaryIncluded(summaryId: String, included: Boolean) = database.withTransaction {
        val summary = novelDao.summary(summaryId) ?: error("总结不存在")
        require(summary.status != SummaryStatus.INVALID) { "这份总结已经失效，暂时不能用于创作" }
        check(novelDao.setSummaryIncluded(summaryId, included) == 1)
        markBaseContextUsageStale(summary.novelId)
    }

    suspend fun updateSummaryContent(summaryId: String, content: String) {
        val summary = novelDao.summary(summaryId) ?: error("总结不存在")
        updateSummary(summaryId, summary.name, content)
    }

    suspend fun updateSummary(summaryId: String, name: String, content: String) = database.withTransaction {
        val summary = novelDao.summary(summaryId) ?: error("总结不存在")
        val prepared = prepareSummaryUpdate(summary, name, content)
        if (!prepared.nameChanged && !prepared.contentChanged) return@withTransaction
        if (prepared.contentChanged) {
            check(novelDao.updateSummaryNameAndContent(summaryId, prepared.updatedSummary.name, prepared.updatedSummary.content, prepared.updatedSummary.updatedAt) == 1)
            markDependentSummaries(summaryId, SummaryStatus.STALE)
            markBaseContextUsageStale(summary.novelId)
        } else {
            check(novelDao.renameSummary(summaryId, prepared.updatedSummary.name, prepared.updatedSummary.updatedAt) == 1)
        }
        novelDao.touchNovel(summary.novelId, prepared.updatedSummary.updatedAt)
    }

    suspend fun deleteSummary(summaryId: String) = database.withTransaction {
        val summary = novelDao.summary(summaryId) ?: error("总结不存在")
        markDependentSummaries(summaryId, SummaryStatus.INVALID)
        check(novelDao.deleteSummary(summaryId) == 1)
        markBaseContextUsageStale(summary.novelId)
        novelDao.touchNovel(summary.novelId)
    }

    suspend fun copyContextEntry(sourceId: String, targetScope: EntryScope, targetNovelId: String?): ContextEntryEntity =
        database.withTransaction {
            val source = novelDao.contextEntry(sourceId) ?: error("条目不存在")
            if (targetScope == EntryScope.NOVEL) requireNotNull(targetNovelId)
            val copy = source.copy(
                id = Ids.newId(),
                scope = targetScope,
                novelId = if (targetScope == EntryScope.NOVEL) targetNovelId else null,
                enabled = if (targetScope == EntryScope.NOVEL) source.enabled else false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            novelDao.insertContextEntry(copy)
            copy.novelId?.let { markBaseContextUsageStale(it) }
            copy
        }

    suspend fun updateNovelModels(
        novelId: String,
        bodyProviderId: String,
        bodyModelId: String,
        summaryProviderId: String? = null,
        summaryModelId: String? = null,
        assistantProviderId: String? = null,
        assistantModelId: String? = null,
    ) {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        novelDao.updateNovel(
            novel.copy(
                bodyProviderId = bodyProviderId,
                bodyModelId = bodyModelId,
                summaryProviderId = summaryProviderId,
                summaryModelId = summaryModelId,
                assistantProviderId = assistantProviderId,
                assistantModelId = assistantModelId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        generationDao.markUsageStale(novelId, TaskType.BODY)
        generationDao.markUsageStale(novelId, TaskType.SUMMARY)
        generationDao.markUsageStale(novelId, TaskType.ASSISTANT)
    }

    suspend fun updateNovelInstructions(
        novelId: String,
        summaryInstruction: String,
        assistantInstruction: String,
    ) = database.withTransaction {
        require(summaryInstruction.isNotBlank()) { "总结指令不能为空" }
        require(assistantInstruction.isNotBlank()) { "助手指令不能为空" }
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        novelDao.updateNovel(
            novel.copy(
                summaryInstruction = summaryInstruction.trim(),
                assistantInstruction = assistantInstruction.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        generationDao.markUsageStale(novelId, TaskType.SUMMARY)
        generationDao.markUsageStale(novelId, TaskType.ASSISTANT)
    }

    suspend fun saveNovelSummaryAsAppDefault(novelId: String) = database.withTransaction {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        require(novel.summaryInstruction.isNotBlank()) { "总结指令不能为空" }
        val existing = novelDao.appSettings()
        novelDao.upsertAppSettings(
            AppSettingsEntity(
                defaultSummaryInstruction = novel.summaryInstruction,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun resetNovelSummaryToAppDefault(novelId: String) = database.withTransaction {
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        var settings = novelDao.appSettings()
        if (settings == null) {
            settings = AppSettingsEntity()
            novelDao.upsertAppSettings(settings)
        }
        novelDao.updateNovel(
            novel.copy(
                summaryInstruction = settings.defaultSummaryInstruction,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        generationDao.markUsageStale(novelId, TaskType.SUMMARY)
    }

    suspend fun resetAppSummaryDefaultToFactory() = database.withTransaction {
        val existing = novelDao.appSettings()
        novelDao.upsertAppSettings(
            AppSettingsEntity(
                defaultSummaryInstruction = com.ainovel.local.data.model.DefaultInstructions.summary,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun acceptBodyCandidate(candidateId: String): AcceptanceRecordEntity = database.withTransaction {
        val candidate = generationDao.candidate(candidateId) ?: error("候选不存在")
        val group = generationDao.candidateGroup(candidate.candidateGroupId) ?: error("这组候选已经不在了")
        require(group.taskType == TaskType.BODY) { "该候选不是正文候选" }
        require(
            group.status == CandidateGroupStatus.PENDING || group.status == CandidateGroupStatus.FAILED,
        ) {
            "这组候选已经处理完毕"
        }
        val chapterId = group.targetChapterId ?: error("正文候选没有目标章节")
        val chapter = novelDao.chapter(chapterId) ?: error("目标章节不存在")
        val appendText = normalizeAppend(chapter.content, candidate.editedText)
        require(appendText.isNotEmpty()) { "候选内容为空" }
        val acceptanceId = Ids.newId()
        val newContent = chapter.content + appendText
        val now = System.currentTimeMillis()
        val acceptance = AcceptanceRecordEntity(
            id = acceptanceId,
            candidateId = candidate.id,
            chapterId = chapterId,
            chapterVersionBefore = chapter.contentVersion,
            chapterVersionAfter = chapter.contentVersion + 1,
            prefixLength = chapter.content.length,
            appendedText = appendText,
            acceptedAt = now,
        )
        generationDao.clearCurrentRevision(chapterId)
        check(novelDao.updateChapterContent(chapterId, newContent, now) == 1)
        generationDao.insertAcceptance(acceptance)
        generationDao.insertRevision(
            ChapterRevisionEntity(
                chapterId = chapterId,
                revisionType = RevisionType.CANDIDATE_ACCEPT,
                contentSnapshot = newContent,
                candidateId = candidate.id,
                acceptanceId = acceptanceId,
                createdAt = now,
            ),
        )
        generationDao.setCandidateAccepted(candidate.id, true, now)
        generationDao.updateGroupState(group.id, CandidateGroupStatus.ACCEPTED, null, now)
        markDependentSummaries(chapterId, SummaryStatus.STALE)
        markBaseContextUsageStale(chapter.novelId)
        novelDao.touchNovel(chapter.novelId, now)
        acceptance
    }

    suspend fun undoAcceptance(acceptanceId: String): UndoAcceptResult = database.withTransaction {
        val acceptance = generationDao.acceptance(acceptanceId) ?: return@withTransaction UndoAcceptResult.NotFound
        if (acceptance.undoneAt != null) return@withTransaction UndoAcceptResult.AlreadyUndone
        val chapter = novelDao.chapter(acceptance.chapterId) ?: return@withTransaction UndoAcceptResult.NotFound
        val expected = acceptance.chapterVersionAfter
        val exactTextStillPresent = chapter.content.length == acceptance.prefixLength + acceptance.appendedText.length &&
            chapter.content.substring(acceptance.prefixLength) == acceptance.appendedText
        if (chapter.contentVersion != expected || !exactTextStillPresent) {
            return@withTransaction UndoAcceptResult.Conflict(chapter.contentVersion, expected)
        }
        val restored = chapter.content.substring(0, acceptance.prefixLength)
        val now = System.currentTimeMillis()
        generationDao.clearCurrentRevision(chapter.id)
        check(novelDao.updateChapterContent(chapter.id, restored, now) == 1)
        generationDao.markAcceptanceUndone(acceptance.id, now)
        generationDao.insertRevision(
            ChapterRevisionEntity(
                chapterId = chapter.id,
                revisionType = RevisionType.ACCEPT_UNDO,
                contentSnapshot = restored,
                candidateId = acceptance.candidateId,
                acceptanceId = acceptance.id,
                createdAt = now,
            ),
        )
        markDependentSummaries(chapter.id, SummaryStatus.STALE)
        markBaseContextUsageStale(chapter.novelId)
        novelDao.touchNovel(chapter.novelId, now)
        UndoAcceptResult.Success
    }

    suspend fun discardCandidateGroup(groupId: String) = database.withTransaction {
        val group = generationDao.candidateGroup(groupId) ?: error("这组候选已经不在了")
        require(
            group.status == CandidateGroupStatus.PENDING || group.status == CandidateGroupStatus.FAILED,
        ) {
            "这组候选已经处理完毕"
        }
        generationDao.updateGroupState(group.id, CandidateGroupStatus.DISCARDED, null, System.currentTimeMillis())
    }

    suspend fun deleteCandidate(candidateId: String): CandidateDeletionResult = database.withTransaction {
        val candidate = generationDao.candidate(candidateId) ?: error("候选不存在或已删除")
        val group = generationDao.candidateGroup(candidate.candidateGroupId) ?: error("这组候选已经不在了")
        require(
            group.status == CandidateGroupStatus.GENERATING ||
                group.status == CandidateGroupStatus.PENDING ||
                group.status == CandidateGroupStatus.FAILED,
        ) { "已经写入正式内容的候选不能在这里删除" }
        require(
            generationDao.acceptanceReferences(candidate.id) == 0 &&
                generationDao.revisionReferences(candidate.id) == 0,
        ) { "该候选已被正文版本引用，不能删除" }
        check(generationDao.deleteCandidate(candidate.id) == 1)
        val remaining = generationDao.candidates(group.id).size
        if (remaining == 0) {
            generationDao.updateGroupState(
                group.id,
                CandidateGroupStatus.DISCARDED,
                group.oneTimeCreativeRequirement,
                System.currentTimeMillis(),
            )
        }
        CandidateDeletionResult(1, remaining, remaining == 0)
    }

    suspend fun deleteAllCandidates(groupId: String): CandidateDeletionResult = database.withTransaction {
        val group = generationDao.candidateGroup(groupId) ?: error("这组候选已经不在了")
        require(
            group.status == CandidateGroupStatus.GENERATING ||
                group.status == CandidateGroupStatus.PENDING ||
                group.status == CandidateGroupStatus.FAILED,
        ) { "这组候选已经处理完毕" }
        val candidates = generationDao.candidates(group.id)
        require(candidates.all { candidate ->
            generationDao.acceptanceReferences(candidate.id) == 0 &&
                generationDao.revisionReferences(candidate.id) == 0
        }) { "这组候选里有内容已经写入正文，因此不能全部删除" }
        val deleted = candidates.sumOf { generationDao.deleteCandidate(it.id) }
        generationDao.updateGroupState(
            group.id,
            CandidateGroupStatus.DISCARDED,
            group.oneTimeCreativeRequirement,
            System.currentTimeMillis(),
        )
        CandidateDeletionResult(deleted, 0, true)
    }

    suspend fun acceptSummaryCandidate(candidateId: String): SummaryEntity = database.withTransaction {
        val candidate = generationDao.candidate(candidateId) ?: error("候选不存在")
        val group = generationDao.candidateGroup(candidate.candidateGroupId) ?: error("这组候选已经不在了")
        require(group.taskType == TaskType.SUMMARY) { "该候选不是总结候选" }
        require(group.status == CandidateGroupStatus.PENDING) { "请先停止生成或等待生成完成" }
        val selections = group.summarySourceSnapshotJson?.let {
            json.decodeFromString<List<SummarySourceSelection>>(it)
        }.orEmpty()
        require(selections.isNotEmpty()) { "总结来源快照缺失" }

        val covered = linkedSetOf<String>()
        val sourceVersions = linkedMapOf<String, Long>()
        val sources = selections.map { selection ->
            when (selection.nodeType) {
                ContextNodeType.CHAPTER -> {
                    val chapter = novelDao.chapter(selection.nodeId)
                        ?.takeUnless { it.deleted }
                        ?: error("总结来源章节已删除")
                    covered += chapter.id
                    sourceVersions[chapter.id] = chapter.contentVersion
                    SummarySourceEntity("", chapter.id, ContextNodeType.CHAPTER, chapter.contentVersion)
                }

                ContextNodeType.SUMMARY -> {
                    val summary = novelDao.summary(selection.nodeId)
                        ?.takeUnless { it.status == SummaryStatus.INVALID }
                        ?: error("这份总结的来源已经失效")
                    covered += decodeStringList(summary.coveredChapterIdsJson)
                    sourceVersions[summary.id] = summary.contentVersion
                    SummarySourceEntity("", summary.id, ContextNodeType.SUMMARY, summary.contentVersion)
                }
            }
        }
        val chapters = novelDao.allChapters(group.novelId).associateBy { it.id }
        val sortedCovered = covered.sortedBy { chapters[it]?.storyOrder ?: Long.MAX_VALUE }
        val logicalPosition = sortedCovered.maxOfOrNull { chapters[it]?.storyOrder ?: 0L } ?: 0L
        val novel = novelDao.novel(group.novelId) ?: error("小说不存在")
        val snapshot = generationDao.requestSnapshot(candidate.requestSnapshotId) ?: error("没有找到这次创作记录")
        val density = parseDensity(snapshot.logicalContextJson)
        val summary = SummaryEntity(
            novelId = group.novelId,
            name = buildSummaryName(sortedCovered, chapters),
            content = candidate.editedText.trim(),
            directSourceNodeIdsJson = json.encodeToString(selections.map { it.nodeId }),
            coveredChapterIdsJson = json.encodeToString(sortedCovered),
            sourceVersionSnapshotJson = json.encodeToString(sourceVersions),
            density = density,
            instructionSnapshot = novel.summaryInstruction,
            logicalPosition = logicalPosition,
        )
        novelDao.insertSummary(summary)
        novelDao.insertSummarySources(sources.map { it.copy(summaryId = summary.id) })
        selections.forEach { selection ->
            when (selection.nodeType) {
                ContextNodeType.CHAPTER -> novelDao.setChapterIncluded(selection.nodeId, false)
                ContextNodeType.SUMMARY -> novelDao.setSummaryIncluded(selection.nodeId, false)
            }
        }
        generationDao.setCandidateAccepted(candidate.id, true)
        generationDao.updateGroupState(group.id, CandidateGroupStatus.ACCEPTED, null, System.currentTimeMillis())
        markBaseContextUsageStale(group.novelId)
        summary
    }

    suspend fun resetAssistantSession(novelId: String) = database.withTransaction {
        novelDao.deleteAssistantSession(novelId)
        generationDao.deleteAssistantUsage(novelId)
        generationDao.deleteAssistantSnapshots(novelId)
        novelDao.upsertAssistantSession(AssistantSessionEntity(novelId = novelId))
    }

    suspend fun setMaxContext(providerId: String, modelId: String, tokens: Long) {
        require(tokens > 0) { "最大上下文必须是大于零的整数" }
        check(modelDao.setMaxContext(providerId, modelId, tokens) == 1)
    }

    suspend fun upsertModel(setting: ModelSettingEntity) {
        require(setting.maxContextTokens > 0)
        modelDao.upsertModel(setting)
    }

    private suspend fun markBaseContextUsageStale(novelId: String) {
        generationDao.markUsageStale(novelId, TaskType.BODY)
        generationDao.markUsageStale(novelId, TaskType.ASSISTANT)
    }

    private suspend fun markDependentSummaries(sourceNodeId: String, status: SummaryStatus) {
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue += sourceNodeId
        while (queue.isNotEmpty()) {
            val source = queue.removeFirst()
            novelDao.summariesDependingOn(source).forEach { dependency ->
                if (visited.add(dependency.summaryId)) {
                    novelDao.setSummaryStatus(dependency.summaryId, status)
                    queue += dependency.summaryId
                }
            }
        }
    }

    private fun normalizeAppend(existing: String, candidateText: String): String {
        val trimmedCandidate = candidateText.trim()
        if (trimmedCandidate.isEmpty()) return ""
        if (existing.isBlank()) return trimmedCandidate
        val trailingNewlines = existing.takeLastWhile { it == '\n' }.length
        val separator = when {
            trailingNewlines >= 2 -> ""
            trailingNewlines == 1 -> "\n"
            else -> "\n\n"
        }
        return separator + trimmedCandidate
    }

    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private fun parseDensity(logicalContextJson: String): SummaryDensity = when {
        "简洁总结" in logicalContextJson -> SummaryDensity.CONCISE
        "详细总结" in logicalContextJson -> SummaryDensity.DETAILED
        else -> SummaryDensity.STANDARD
    }

    private fun buildSummaryName(
        coveredIds: List<String>,
        chapters: Map<String, ChapterEntity>,
    ): String {
        val orders = coveredIds.mapNotNull { chapters[it]?.storyOrder }
            .map { (it / ORDER_STEP).toInt().coerceAtLeast(1) }
            .distinct()
            .sorted()
        if (orders.isEmpty()) return "章节总结"
        val ranges = mutableListOf<String>()
        var start = orders.first()
        var end = start
        for (number in orders.drop(1)) {
            if (number == end + 1) {
                end = number
            } else {
                ranges += if (start == end) "$start" else "$start–$end"
                start = number
                end = number
            }
        }
        ranges += if (start == end) "$start" else "$start–$end"
        return "第${ranges.joinToString("、")}章总结"
    }

    private companion object {
        const val ORDER_STEP = 1_000L
    }
}
