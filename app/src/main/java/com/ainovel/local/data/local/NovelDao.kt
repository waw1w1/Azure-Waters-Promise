package com.ainovel.local.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainovel.local.data.model.AssistantSessionEntity
import com.ainovel.local.data.model.AppSettingsEntity
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.NovelEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.SummarySourceEntity
import com.ainovel.local.data.model.SummaryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM app_settings WHERE id = 'app'")
    fun observeAppSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 'app'")
    suspend fun appSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM novels ORDER BY updatedAt DESC")
    fun observeNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels ORDER BY updatedAt DESC")
    suspend fun allNovels(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun novel(id: String): NovelEntity?

    @Query("SELECT * FROM novels WHERE id = :id")
    fun observeNovel(id: String): Flow<NovelEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNovel(novel: NovelEntity)

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Query("UPDATE novels SET updatedAt = :updatedAt WHERE id = :novelId")
    suspend fun touchNovel(novelId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET coverFileName = :coverFileName, updatedAt = :updatedAt WHERE id = :novelId")
    suspend fun updateNovelCover(
        novelId: String,
        coverFileName: String?,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query(
        """UPDATE novels SET
            bodyGenerationSettingsJson = :bodyJson,
            summaryGenerationSettingsJson = :summaryJson,
            assistantGenerationSettingsJson = :assistantJson,
            updatedAt = :updatedAt
            WHERE id = :novelId""",
    )
    suspend fun updateGenerationSettings(
        novelId: String,
        bodyJson: String?,
        summaryJson: String?,
        assistantJson: String?,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND deleted = 0 ORDER BY storyOrder")
    fun observeActiveChapters(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY storyOrder")
    fun observeAllChapters(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY storyOrder")
    suspend fun allChapters(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun chapter(id: String): ChapterEntity?

    @Query("SELECT COALESCE(MAX(storyOrder), 0) FROM chapters WHERE novelId = :novelId")
    suspend fun maxChapterOrder(novelId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET content = :content, contentVersion = contentVersion + 1, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun updateChapterContent(chapterId: String, content: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE chapters SET name = :name, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun renameChapter(chapterId: String, name: String?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE chapters SET includeInContext = :included, updatedAt = :updatedAt WHERE id = :chapterId AND deleted = 0")
    suspend fun setChapterIncluded(chapterId: String, included: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE chapters SET deleted = 1, deletedAt = :deletedAt, includeInContext = 0, updatedAt = :deletedAt WHERE id = :chapterId")
    suspend fun softDeleteChapter(chapterId: String, deletedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE chapters SET deleted = 0, deletedAt = NULL, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun restoreChapter(chapterId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE chapters SET storyOrder = :storyOrder, updatedAt = :updatedAt WHERE id = :chapterId")
    suspend fun setChapterOrder(chapterId: String, storyOrder: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM chapters WHERE id = :chapterId AND content = '' AND contentVersion = 0")
    suspend fun deletePristineChapter(chapterId: String): Int

    @Query("DELETE FROM chapters WHERE id = :chapterId AND deleted = 1")
    suspend fun permanentlyDeleteChapter(chapterId: String): Int

    @Query("SELECT * FROM summaries WHERE novelId = :novelId ORDER BY logicalPosition, createdAt")
    fun observeSummaries(novelId: String): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE novelId = :novelId ORDER BY logicalPosition, createdAt")
    suspend fun allSummaries(novelId: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE id = :id")
    suspend fun summary(id: String): SummaryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSummary(summary: SummaryEntity)

    @Query("UPDATE summaries SET name = :name, updatedAt = :updatedAt WHERE id = :summaryId")
    suspend fun renameSummary(summaryId: String, name: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE summaries SET content = :content, contentVersion = contentVersion + 1, updatedAt = :updatedAt WHERE id = :summaryId")
    suspend fun updateSummaryContent(summaryId: String, content: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE summaries SET name = :name, content = :content, contentVersion = contentVersion + 1, updatedAt = :updatedAt WHERE id = :summaryId")
    suspend fun updateSummaryNameAndContent(summaryId: String, name: String, content: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummarySources(sources: List<SummarySourceEntity>)

    @Query("SELECT * FROM summary_sources WHERE summaryId = :summaryId")
    suspend fun sourcesForSummary(summaryId: String): List<SummarySourceEntity>

    @Query("SELECT * FROM summary_sources WHERE sourceNodeId = :sourceNodeId")
    suspend fun summariesDependingOn(sourceNodeId: String): List<SummarySourceEntity>

    @Query("UPDATE summaries SET includeInContext = :included, updatedAt = :updatedAt WHERE id = :summaryId AND status != 'INVALID'")
    suspend fun setSummaryIncluded(summaryId: String, included: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE summaries SET status = :status, includeInContext = CASE WHEN :status = 'INVALID' THEN 0 ELSE includeInContext END, updatedAt = :updatedAt WHERE id = :summaryId")
    suspend fun setSummaryStatus(summaryId: String, status: SummaryStatus, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM summaries WHERE id = :summaryId")
    suspend fun deleteSummary(summaryId: String): Int

    @Query("SELECT * FROM context_entries WHERE scope = :scope AND type = :type AND (:novelId IS NULL OR novelId = :novelId) ORDER BY sortOrder")
    fun observeContextEntries(scope: EntryScope, type: EntryType, novelId: String?): Flow<List<ContextEntryEntity>>

    @Query("SELECT * FROM context_entries WHERE novelId = :novelId AND scope = 'NOVEL' AND enabled = 1 ORDER BY type, sortOrder")
    suspend fun enabledNovelEntries(novelId: String): List<ContextEntryEntity>

    @Query("SELECT * FROM context_entries ORDER BY scope, type, sortOrder")
    suspend fun allContextEntries(): List<ContextEntryEntity>

    @Query("SELECT * FROM context_entries WHERE id = :id")
    suspend fun contextEntry(id: String): ContextEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContextEntry(entry: ContextEntryEntity)

    @Update
    suspend fun updateContextEntry(entry: ContextEntryEntity)

    @Delete
    suspend fun deleteContextEntry(entry: ContextEntryEntity)

    @Query("SELECT * FROM provider_configs ORDER BY displayName")
    fun observeProviders(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs")
    suspend fun allProviders(): List<ProviderConfigEntity>

    @Query("SELECT * FROM provider_configs WHERE providerId = :providerId")
    suspend fun provider(providerId: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: ProviderConfigEntity)

    @Query("SELECT * FROM assistant_sessions WHERE novelId = :novelId")
    suspend fun assistantSession(novelId: String): AssistantSessionEntity?

    @Query("SELECT * FROM assistant_sessions WHERE novelId = :novelId")
    fun observeAssistantSession(novelId: String): Flow<AssistantSessionEntity?>

    @Query("SELECT * FROM assistant_sessions")
    suspend fun allAssistantSessions(): List<AssistantSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssistantSession(session: AssistantSessionEntity)

    @Query("DELETE FROM assistant_sessions WHERE novelId = :novelId")
    suspend fun deleteAssistantSession(novelId: String)
}
