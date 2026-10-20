package com.ainovel.local.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainovel.local.data.model.AcceptanceRecordEntity
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.ChapterRevisionEntity
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.ResponseStatus
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.model.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRequestSnapshot(snapshot: RequestSnapshotEntity)

    @Query("SELECT * FROM request_snapshots WHERE id = :id")
    suspend fun requestSnapshot(id: String): RequestSnapshotEntity?

    @Query("SELECT * FROM request_snapshots ORDER BY requestedAt DESC")
    suspend fun allRequestSnapshots(): List<RequestSnapshotEntity>

    @Query("SELECT * FROM request_snapshots WHERE novelId = :novelId ORDER BY requestedAt DESC")
    fun observeRequestSnapshots(novelId: String): Flow<List<RequestSnapshotEntity>>

    @Query("SELECT * FROM request_snapshots WHERE candidateGroupId = :groupId ORDER BY requestedAt")
    suspend fun snapshotsForGroup(groupId: String): List<RequestSnapshotEntity>

    @Query("DELETE FROM request_snapshots WHERE id = :id")
    suspend fun deleteRequestSnapshot(id: String): Int

    @Query("SELECT COUNT(*) FROM candidate_groups WHERE frozenRequestSnapshotId = :requestId")
    suspend fun frozenGroupReferences(requestId: String): Int

    @Query("SELECT COUNT(*) FROM candidate_groups WHERE frozenRequestSnapshotId = :requestId AND status IN ('GENERATING', 'PENDING', 'FAILED')")
    suspend fun activeFrozenGroupReferences(requestId: String): Int

    @Query("SELECT COUNT(*) FROM candidates WHERE requestSnapshotId = :requestId")
    suspend fun candidateReferences(requestId: String): Int

    @Query("SELECT COUNT(*) FROM candidates INNER JOIN candidate_groups ON candidate_groups.id = candidates.candidateGroupId WHERE candidates.requestSnapshotId = :requestId AND candidate_groups.status IN ('GENERATING', 'PENDING', 'FAILED')")
    suspend fun activeCandidateReferences(requestId: String): Int

    @Query("SELECT COUNT(*) FROM acceptance_records WHERE candidateId = :candidateId")
    suspend fun acceptanceReferences(candidateId: String): Int

    @Query("SELECT COUNT(*) FROM chapter_revisions WHERE candidateId = :candidateId")
    suspend fun revisionReferences(candidateId: String): Int

    @Query("SELECT * FROM candidates WHERE requestSnapshotId = :requestId")
    suspend fun candidatesForRequest(requestId: String): List<CandidateEntity>

    @Query("UPDATE request_snapshots SET responseStatus = :status, errorJson = :errorJson, usageMissing = :usageMissing, usageParseErrorJson = :usageParseErrorJson, completedAt = :completedAt WHERE id = :id")
    suspend fun finishRequest(
        id: String,
        status: ResponseStatus,
        errorJson: String? = null,
        usageMissing: Boolean = false,
        usageParseErrorJson: String? = null,
        completedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query("UPDATE request_snapshots SET responseStatus = 'STREAMING', completedAt = NULL WHERE id = :id")
    suspend fun markRequestStreaming(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCandidateGroup(group: CandidateGroupEntity)

    @Query("SELECT * FROM candidate_groups WHERE id = :id")
    suspend fun candidateGroup(id: String): CandidateGroupEntity?

    @Query("SELECT * FROM candidate_groups ORDER BY createdAt")
    suspend fun allCandidateGroups(): List<CandidateGroupEntity>

    @Query("DELETE FROM candidate_groups WHERE id = :id")
    suspend fun deleteCandidateGroup(id: String): Int

    @Query("SELECT * FROM candidate_groups WHERE novelId = :novelId AND status IN ('GENERATING', 'PENDING', 'FAILED') ORDER BY createdAt DESC")
    fun observeActiveGroups(novelId: String): Flow<List<CandidateGroupEntity>>

    @Query("UPDATE candidate_groups SET status = :status, oneTimeCreativeRequirement = :requirement, endedAt = :endedAt WHERE id = :id")
    suspend fun updateGroupState(
        id: String,
        status: CandidateGroupStatus,
        requirement: String?,
        endedAt: Long?,
    ): Int

    @Query("UPDATE candidate_groups SET status = 'PENDING' WHERE status = 'GENERATING'")
    suspend fun recoverInterruptedGroups(): Int

    @Query("UPDATE request_snapshots SET responseStatus = 'PARTIAL', usageMissing = 1, completedAt = :completedAt WHERE responseStatus = 'STREAMING'")
    suspend fun recoverInterruptedRequests(completedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCandidate(candidate: CandidateEntity)

    @Update
    suspend fun updateCandidate(candidate: CandidateEntity)

    @Query("SELECT * FROM candidates WHERE id = :id")
    suspend fun candidate(id: String): CandidateEntity?

    @Query("SELECT * FROM candidates ORDER BY createdAt")
    suspend fun allCandidates(): List<CandidateEntity>

    @Query("SELECT * FROM candidates WHERE candidateGroupId = :groupId ORDER BY rollNumber")
    fun observeCandidates(groupId: String): Flow<List<CandidateEntity>>

    @Query("SELECT candidates.* FROM candidates INNER JOIN candidate_groups ON candidate_groups.id = candidates.candidateGroupId WHERE candidate_groups.novelId = :novelId ORDER BY candidates.createdAt DESC")
    fun observeNovelCandidates(novelId: String): Flow<List<CandidateEntity>>

    @Query("SELECT * FROM candidates WHERE candidateGroupId = :groupId ORDER BY rollNumber")
    suspend fun candidates(groupId: String): List<CandidateEntity>

    @Query("DELETE FROM candidates WHERE id = :id")
    suspend fun deleteCandidate(id: String): Int

    @Query("SELECT COALESCE(MAX(rollNumber), 0) FROM candidates WHERE candidateGroupId = :groupId")
    suspend fun maxRollNumber(groupId: String): Int

    @Query("UPDATE candidates SET rawOutput = :rawOutput, editedText = :editedText, reasoningContent = :reasoningContent, complete = :complete, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCandidateStream(
        id: String,
        rawOutput: String,
        editedText: String,
        reasoningContent: String?,
        complete: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query("UPDATE candidates SET editedText = :editedText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun editCandidate(id: String, editedText: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE candidates SET accepted = :accepted, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCandidateAccepted(id: String, accepted: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE candidates SET usageRecordId = :usageRecordId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCandidateUsage(id: String, usageRecordId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUsage(usage: UsageRecordEntity)

    @Query("SELECT * FROM usage_records WHERE id = :id")
    suspend fun usage(id: String): UsageRecordEntity?

    @Query("SELECT * FROM usage_records ORDER BY createdAt")
    suspend fun allUsage(): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_records WHERE novelId = :novelId AND taskType = :taskType ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestUsage(novelId: String, taskType: TaskType): Flow<UsageRecordEntity?>

    @Query("SELECT * FROM usage_records WHERE novelId = :novelId ORDER BY createdAt DESC")
    fun observeNovelUsage(novelId: String): Flow<List<UsageRecordEntity>>

    @Query("UPDATE usage_records SET staleAgainstCurrentContent = 1 WHERE novelId = :novelId AND taskType = :taskType")
    suspend fun markUsageStale(novelId: String, taskType: TaskType)

    @Query("DELETE FROM usage_records WHERE requestSnapshotId = :requestSnapshotId")
    suspend fun deleteUsageForRequest(requestSnapshotId: String): Int

    @Query("DELETE FROM usage_records WHERE novelId = :novelId AND taskType = 'ASSISTANT'")
    suspend fun deleteAssistantUsage(novelId: String): Int

    @Query("DELETE FROM request_snapshots WHERE novelId = :novelId AND taskType = 'ASSISTANT'")
    suspend fun deleteAssistantSnapshots(novelId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: ChapterRevisionEntity)

    @Query("UPDATE chapter_revisions SET current = 0 WHERE chapterId = :chapterId")
    suspend fun clearCurrentRevision(chapterId: String)

    @Query("SELECT * FROM chapter_revisions ORDER BY createdAt")
    suspend fun allRevisions(): List<ChapterRevisionEntity>

    @Query("SELECT * FROM chapter_revisions WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    fun observeRevisions(chapterId: String): Flow<List<ChapterRevisionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAcceptance(record: AcceptanceRecordEntity)

    @Query("SELECT * FROM acceptance_records WHERE id = :id")
    suspend fun acceptance(id: String): AcceptanceRecordEntity?

    @Query("SELECT * FROM acceptance_records ORDER BY acceptedAt")
    suspend fun allAcceptances(): List<AcceptanceRecordEntity>

    @Query("SELECT acceptance_records.* FROM acceptance_records INNER JOIN chapters ON chapters.id = acceptance_records.chapterId WHERE chapters.novelId = :novelId ORDER BY acceptedAt DESC")
    fun observeAcceptances(novelId: String): Flow<List<AcceptanceRecordEntity>>

    @Query("UPDATE acceptance_records SET undoneAt = :undoneAt WHERE id = :id AND undoneAt IS NULL")
    suspend fun markAcceptanceUndone(id: String, undoneAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM acceptance_records WHERE chapterId = :chapterId")
    suspend fun deleteAcceptancesForChapter(chapterId: String): Int
}
