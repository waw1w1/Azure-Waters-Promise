package com.ainovel.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.ContextNodeType
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.ResponseStatus
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.SummaryDensity
import com.ainovel.local.data.model.SummaryStatus
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.ProviderException
import com.ainovel.local.domain.SummarySourceSelection
import com.ainovel.local.domain.UndoAcceptResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreWorkflowTest {
    @Test
    fun refreshFailure_preservesPreviouslyValidatedModelCache() = runBlocking {
        RepositoryFixture(
            responses = emptyList(),
            modelListFailure = ProviderException(retryable = true, message = "刷新失败"),
        ).use { fixture ->
            fixture.configuredNovel()
            val before = fixture.database.modelDao().models("fake")

            assertTrue(runCatching { fixture.generation.refreshModels("fake") }.isFailure)
            assertEquals(before, fixture.database.modelDao().models("fake"))
        }
    }

    @Test
    fun generatingCandidateRejectsMutation_andProcessRecoveryMakesPartialCandidateActionable() = runBlocking {
        RepositoryFixture(emptyList()).use { fixture ->
            val novel = fixture.novels.createNovel("恢复测试")
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            val snapshot = RequestSnapshotEntity(
                taskType = TaskType.BODY,
                novelId = novel.id,
                targetEntityId = chapter.id,
                candidateGroupId = null,
                providerId = "fake",
                modelId = "fake-model",
                logicalContextJson = "[]",
                finalMessagesJson = "[]",
                generationParametersJson = "{}",
                redactedRequestBody = "{}",
                responseStatus = ResponseStatus.STREAMING,
            )
            fixture.database.generationDao().insertRequestSnapshot(snapshot)
            val group = CandidateGroupEntity(
                taskType = TaskType.BODY,
                novelId = novel.id,
                targetChapterId = chapter.id,
                oneTimeCreativeRequirement = "继续写",
                frozenRequestSnapshotId = snapshot.id,
                status = CandidateGroupStatus.GENERATING,
            )
            fixture.database.generationDao().insertCandidateGroup(group)
            val candidate = CandidateEntity(
                candidateGroupId = group.id,
                rollNumber = 1,
                rawOutput = "已持久化的部分",
                editedText = "已持久化的部分",
                complete = false,
                requestSnapshotId = snapshot.id,
            )
            fixture.database.generationDao().insertCandidate(candidate)

            assertTrue(runCatching { fixture.generation.editCandidate(candidate.id, "竞态修改") }.isFailure)
            assertTrue(runCatching { fixture.novels.acceptBodyCandidate(candidate.id) }.isFailure)

            fixture.generation.recoverInterruptedGenerationState()
            assertEquals(CandidateGroupStatus.PENDING, fixture.database.generationDao().candidateGroup(group.id)?.status)
            assertEquals(ResponseStatus.PARTIAL, fixture.database.generationDao().requestSnapshot(snapshot.id)?.responseStatus)
            assertTrue(fixture.database.generationDao().requestSnapshot(snapshot.id)?.usageMissing == true)
            fixture.novels.acceptBodyCandidate(candidate.id)
            assertEquals("已持久化的部分", fixture.novels.chapter(chapter.id)?.content)
        }
    }

    @Test
    fun candidateDeletion_removesReasoningOnlyCandidate_andDiscardsEmptyGroup() = runBlocking {
        RepositoryFixture(emptyList()).use { fixture ->
            val novel = fixture.novels.createNovel("候选删除")
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            val snapshot = RequestSnapshotEntity(
                taskType = TaskType.BODY,
                novelId = novel.id,
                targetEntityId = chapter.id,
                candidateGroupId = null,
                providerId = "fake",
                modelId = "fake-model",
                logicalContextJson = "[]",
                finalMessagesJson = "[]",
                generationParametersJson = "{}",
                redactedRequestBody = "{}",
                responseStatus = ResponseStatus.PARTIAL,
            )
            fixture.database.generationDao().insertRequestSnapshot(snapshot)
            val group = CandidateGroupEntity(
                taskType = TaskType.BODY,
                novelId = novel.id,
                targetChapterId = chapter.id,
                frozenRequestSnapshotId = snapshot.id,
                status = CandidateGroupStatus.PENDING,
            )
            fixture.database.generationDao().insertCandidateGroup(group)
            val reasoningOnly = CandidateEntity(
                candidateGroupId = group.id,
                rollNumber = 1,
                reasoningContent = "正文尚未开始的思维链",
                requestSnapshotId = snapshot.id,
            )
            fixture.database.generationDao().insertCandidate(reasoningOnly)

            val result = fixture.novels.deleteCandidate(reasoningOnly.id)

            assertEquals(1, result.deletedCount)
            assertEquals(0, result.remainingCount)
            assertTrue(result.groupDiscarded)
            assertNull(fixture.database.generationDao().candidate(reasoningOnly.id))
            assertEquals(CandidateGroupStatus.DISCARDED, fixture.database.generationDao().candidateGroup(group.id)?.status)
            assertTrue(fixture.database.generationDao().requestSnapshot(snapshot.id) != null)
        }
    }

    @Test
    fun authenticationFailure_doesNotOverwritePreviouslyValidConfigOrSecret() = runBlocking {
        RepositoryFixture(
            responses = emptyList(),
            modelListFailure = ProviderException(statusCode = 401, retryable = false, message = "密钥无效"),
        ).use { fixture ->
            fixture.configuredNovel()
            val before = fixture.database.novelDao().provider("fake")!!

            val result = runCatching {
                fixture.generation.saveAndValidateProvider(
                    providerId = "fake",
                    displayName = "Fake",
                    baseUrl = "https://wrong.example.invalid",
                    apiKey = "wrong-secret",
                )
            }

            assertTrue(result.isFailure)
            assertEquals(before, fixture.database.novelDao().provider("fake"))
            assertEquals("unit-test-secret", fixture.secrets.get(before.secretReference))
        }
    }

    @Test
    fun modelListFailure_preservesEncryptedConfigAndManualValidationCompletesSetup() = runBlocking {
        RepositoryFixture(
            responses = listOf(FakeResponse("连接成功")),
            modelListFailure = ProviderException(retryable = true, message = "模型列表暂时不可用"),
        ).use { fixture ->
            val result = runCatching {
                fixture.generation.saveAndValidateProvider(
                    providerId = "fake",
                    displayName = "Fake",
                    baseUrl = "https://fallback.example.invalid",
                    apiKey = "fallback-secret",
                )
            }
            assertTrue(result.isFailure)
            val pending = fixture.database.novelDao().provider("fake")!!
            assertEquals("MODEL_LIST_UNAVAILABLE", pending.validationState)
            assertEquals("fallback-secret", fixture.secrets.get(pending.secretReference))

            val model = fixture.generation.validateManualModel("fake", "manual-model")
            assertTrue(model.manualModel)
            assertTrue(model.validated)
            assertEquals("VERIFIED_MANUAL", fixture.database.novelDao().provider("fake")?.validationState)
        }
    }

    @Test
    fun providerValidation_blankKeyReusesEncryptedExistingSecret() = runBlocking {
        RepositoryFixture(emptyList()).use { fixture ->
            fixture.generation.saveAndValidateProvider(
                providerId = "fake",
                displayName = "Fake",
                baseUrl = "https://first.example.invalid",
                apiKey = "first-secret",
            )

            fixture.generation.saveAndValidateProvider(
                providerId = "fake",
                displayName = "Fake",
                baseUrl = "https://second.example.invalid",
                apiKey = "",
            )

            val provider = fixture.database.novelDao().provider("fake")!!
            assertEquals("https://second.example.invalid", provider.baseUrl)
            assertEquals("first-secret", fixture.secrets.get(provider.secretReference))
        }
    }

    @Test
    fun bodyCandidate_roll_acceptAndUndo_preservesTraceability() = runBlocking {
        RepositoryFixture(
            listOf(
                FakeResponse("模型初稿", reasoning = "不应写入正文"),
                FakeResponse("第二个 Take"),
            ),
        ).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.novels.saveChapterContent(chapter.id, "已有正文")

            fixture.generation.generateBody(
                novel.id,
                chapter.id,
                "只续写一段",
                GenerationSettings(thinkingEnabled = true),
            )
            assertEquals("已有正文", fixture.novels.chapter(chapter.id)?.content)
            val group = fixture.database.generationDao().allCandidateGroups().single()
            val firstCandidate = fixture.database.generationDao().candidates(group.id).single()
            assertEquals("模型初稿", firstCandidate.rawOutput)
            assertEquals("不应写入正文", firstCandidate.reasoningContent)

            val frozen = fixture.database.generationDao().requestSnapshot(group.frozenRequestSnapshotId)!!
            fixture.novels.saveChapterContent(chapter.id, "已有正文\n\n手动新增")
            fixture.generation.roll(group.id)
            val snapshots = fixture.database.generationDao().allRequestSnapshots()
            val rollSnapshot = snapshots.first { it.id != frozen.id }
            assertNotEquals(frozen.id, rollSnapshot.id)
            assertEquals(frozen.finalMessagesJson, rollSnapshot.finalMessagesJson)
            assertEquals(frozen.generationParametersJson, rollSnapshot.generationParametersJson)
            assertEquals(frozen.redactedRequestBody, rollSnapshot.redactedRequestBody)

            fixture.generation.editCandidate(firstCandidate.id, "作者修改稿")
            val acceptance = fixture.novels.acceptBodyCandidate(firstCandidate.id)
            val acceptedChapter = fixture.novels.chapter(chapter.id)!!
            assertEquals("已有正文\n\n手动新增\n\n作者修改稿", acceptedChapter.content)
            assertFalse(acceptedChapter.content.contains("不应写入正文"))
            val savedCandidate = fixture.novels.candidate(firstCandidate.id)!!
            assertEquals("模型初稿", savedCandidate.rawOutput)
            assertEquals("作者修改稿", savedCandidate.editedText)
            assertEquals(CandidateGroupStatus.ACCEPTED, fixture.novels.candidateGroup(group.id)?.status)
            assertNull(fixture.novels.candidateGroup(group.id)?.oneTimeCreativeRequirement)

            assertEquals(UndoAcceptResult.Success, fixture.novels.undoAcceptance(acceptance.id))
            assertEquals("已有正文\n\n手动新增", fixture.novels.chapter(chapter.id)?.content)
            assertEquals(2, fixture.database.generationDao().candidates(group.id).size)
            assertTrue(fixture.database.generationDao().allRevisions().any { it.acceptanceId == acceptance.id })
        }
    }

    @Test
    fun emptyFailure_keepsRequirementAndCreatesNoCandidate() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("", usage = null))).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.generation.generateBody(novel.id, chapter.id, "保留这条要求", GenerationSettings())
            val group = fixture.database.generationDao().allCandidateGroups().single()
            assertEquals(CandidateGroupStatus.FAILED, fixture.novels.candidateGroup(group.id)?.status)
            assertEquals("保留这条要求", fixture.novels.candidateGroup(group.id)?.oneTimeCreativeRequirement)
            assertTrue(fixture.database.generationDao().candidates(group.id).isEmpty())
            assertEquals(ResponseStatus.FAILED, fixture.database.generationDao().allRequestSnapshots().single().responseStatus)
            assertEquals("", fixture.novels.chapter(chapter.id)?.content)
        }
    }

    @Test
    fun incompleteWithoutUsage_isStillAcceptableAndNeverEstimated() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("已收到的部分", complete = false, usage = null))).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.generation.generateBody(novel.id, chapter.id, "部分响应测试", GenerationSettings())
            val group = fixture.database.generationDao().allCandidateGroups().single()
            val candidate = fixture.database.generationDao().candidates(group.id).single()
            assertFalse(candidate.complete)
            assertTrue(candidate.usageRecordId == null)
            assertTrue(fixture.database.generationDao().allUsage().isEmpty())
            assertTrue(fixture.database.generationDao().allRequestSnapshots().single().usageMissing)
            fixture.novels.acceptBodyCandidate(candidate.id)
            assertEquals("已收到的部分", fixture.novels.chapter(chapter.id)?.content)
        }
    }

    @Test
    fun summaryAcceptanceAndDependencyStatus_areRecursiveAndTransactional() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("第一级总结"), FakeResponse("第二级总结"))).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.novels.saveChapterContent(chapter.id, "第一章正文")
            fixture.generation.generateSummary(
                novel.id,
                listOf(SummarySourceSelection(chapter.id, ContextNodeType.CHAPTER)),
                SummaryDensity.STANDARD,
                GenerationSettings(),
            )
            val firstCandidate = fixture.database.generationDao().allCandidates().single()
            val firstSummary = fixture.novels.acceptSummaryCandidate(firstCandidate.id)
            assertFalse(fixture.novels.chapter(chapter.id)!!.includeInContext)
            assertTrue(fixture.database.novelDao().summary(firstSummary.id)!!.includeInContext)
            assertEquals(listOf(chapter.id), fixture.json.decodeFromString<List<String>>(firstSummary.coveredChapterIdsJson))

            fixture.generation.generateSummary(
                novel.id,
                listOf(SummarySourceSelection(firstSummary.id, ContextNodeType.SUMMARY)),
                SummaryDensity.DETAILED,
                GenerationSettings(),
            )
            val secondCandidate = fixture.database.generationDao().allCandidates().first { it.id != firstCandidate.id }
            val secondSummary = fixture.novels.acceptSummaryCandidate(secondCandidate.id)
            assertFalse(fixture.database.novelDao().summary(firstSummary.id)!!.includeInContext)
            assertTrue(fixture.database.novelDao().summary(secondSummary.id)!!.includeInContext)
            assertEquals(listOf(chapter.id), fixture.json.decodeFromString<List<String>>(secondSummary.coveredChapterIdsJson))

            fixture.novels.saveChapterContent(chapter.id, "第一章已修改")
            assertEquals(SummaryStatus.STALE, fixture.database.novelDao().summary(firstSummary.id)?.status)
            assertEquals(SummaryStatus.STALE, fixture.database.novelDao().summary(secondSummary.id)?.status)

            fixture.novels.reorderChapter(chapter.id, 2_000L)
            assertEquals(SummaryStatus.INVALID, fixture.database.novelDao().summary(firstSummary.id)?.status)
            assertEquals(SummaryStatus.INVALID, fixture.database.novelDao().summary(secondSummary.id)?.status)
            assertFalse(fixture.database.novelDao().summary(firstSummary.id)!!.includeInContext)
            assertFalse(fixture.database.novelDao().summary(secondSummary.id)!!.includeInContext)
        }
    }

    @Test
    fun summaryTitleAndContentEdit_andDeleteSummary_invalidatesDependentsAndRemovesSummary() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("第一级总结"), FakeResponse("第二级总结"))).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.novels.saveChapterContent(chapter.id, "第一章正文")
            fixture.generation.generateSummary(
                novel.id,
                listOf(SummarySourceSelection(chapter.id, ContextNodeType.CHAPTER)),
                SummaryDensity.STANDARD,
                GenerationSettings(),
            )
            val firstCandidate = fixture.database.generationDao().allCandidates().single()
            val firstSummary = fixture.novels.acceptSummaryCandidate(firstCandidate.id)

            fixture.generation.generateSummary(
                novel.id,
                listOf(SummarySourceSelection(firstSummary.id, ContextNodeType.SUMMARY)),
                SummaryDensity.DETAILED,
                GenerationSettings(),
            )
            val secondCandidate = fixture.database.generationDao().allCandidates().first { it.id != firstCandidate.id }
            val secondSummary = fixture.novels.acceptSummaryCandidate(secondCandidate.id)

            // Renaming summary title only should not mark dependent summary as STALE
            fixture.novels.updateSummary(firstSummary.id, "自定义第一卷总结", firstSummary.content)
            assertEquals("自定义第一卷总结", fixture.database.novelDao().summary(firstSummary.id)?.name)
            assertEquals(SummaryStatus.VALID, fixture.database.novelDao().summary(secondSummary.id)?.status)

            // Updating summary content should mark dependent summary as STALE
            fixture.novels.updateSummary(firstSummary.id, "自定义第一卷总结", "更新后的第一级总结内容")
            assertEquals("更新后的第一级总结内容", fixture.database.novelDao().summary(firstSummary.id)?.content)
            assertEquals(SummaryStatus.STALE, fixture.database.novelDao().summary(secondSummary.id)?.status)

            // Deleting firstSummary removes it and marks dependent secondSummary as INVALID
            fixture.novels.deleteSummary(firstSummary.id)
            assertNull(fixture.database.novelDao().summary(firstSummary.id))
            assertEquals(SummaryStatus.INVALID, fixture.database.novelDao().summary(secondSummary.id)?.status)
            assertFalse(fixture.database.novelDao().summary(secondSummary.id)!!.includeInContext)
        }
    }

    @Test
    fun assembler_excludesGlobalAndManagementNamesAndUsesRequiredOrder() = runBlocking {
        RepositoryFixture(emptyList()).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.novels.saveChapterContent(chapter.id, "已接受正文内容")
            val globalName = "GLOBAL-NAME-MUST-NOT-SEND"
            fixture.novels.upsertContextEntry(ContextEntryEntity(type = EntryType.PRESET, scope = EntryScope.GLOBAL, name = globalName, content = "全局内容", enabled = true, sortOrder = 1))
            fixture.novels.upsertContextEntry(ContextEntryEntity(type = EntryType.PRESET, scope = EntryScope.NOVEL, novelId = novel.id, name = "PRESET-MANAGEMENT-NAME", content = "预设内容", sortOrder = 2))
            fixture.novels.upsertContextEntry(ContextEntryEntity(type = EntryType.WORLDBOOK, scope = EntryScope.NOVEL, novelId = novel.id, name = "WORLDBOOK-MANAGEMENT-NAME", content = "世界书内容", sortOrder = 1))

            val prepared = fixture.assembler.prepareBody(
                novelId = novel.id,
                targetChapterId = chapter.id,
                providerId = "fake",
                modelId = "fake-model",
                oneTimeCreativeRequirement = "本次要求",
                settings = GenerationSettings(),
                adapter = fixture.adapter,
            )
            assertEquals(listOf("PRESET", "WORLDBOOK", "CHAPTER", "ONE_TIME_CREATIVE_REQUIREMENT"), prepared.logicalContext.map { it.sourceType })
            val actualRequest = prepared.serializedRequestBody
            assertFalse(actualRequest.contains(globalName))
            assertFalse(actualRequest.contains("全局内容"))
            assertFalse(actualRequest.contains("PRESET-MANAGEMENT-NAME"))
            assertFalse(actualRequest.contains("WORLDBOOK-MANAGEMENT-NAME"))
            assertEquals(
                listOf("预设内容", "世界书内容", "已接受正文内容", "本次要求"),
                prepared.logicalContext.map { it.transmittedContent },
            )
            assertEquals(
                listOf("system", "system", "system", "user"),
                prepared.messages.map { it.role },
            )
        }
    }

    @Test
    fun chapterRecycleAndReorder_preserveContentAndInvalidateDependentSummary() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("第一章总结"))).use { fixture ->
            val novel = fixture.configuredNovel()
            val first = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.novels.saveChapterContent(first.id, "不会丢失的正文")
            val second = fixture.novels.createNextChapter(novel.id)
            fixture.novels.saveChapterContent(second.id, "第二章正文")
            fixture.generation.generateSummary(
                novel.id,
                listOf(SummarySourceSelection(first.id, ContextNodeType.CHAPTER)),
                SummaryDensity.STANDARD,
                GenerationSettings(),
            )
            val summary = fixture.novels.acceptSummaryCandidate(fixture.database.generationDao().allCandidates().single().id)

            fixture.novels.swapChapterOrder(first.id, second.id)
            assertEquals(SummaryStatus.INVALID, fixture.database.novelDao().summary(summary.id)?.status)
            fixture.novels.softDeleteChapter(first.id)
            assertTrue(fixture.novels.chapter(first.id)?.deleted == true)
            assertEquals("不会丢失的正文", fixture.novels.chapter(first.id)?.content)
            fixture.novels.restoreChapter(first.id)
            assertFalse(fixture.novels.chapter(first.id)!!.deleted)
            assertEquals(SummaryStatus.INVALID, fixture.database.novelDao().summary(summary.id)?.status)

            // Cannot permanently delete an active chapter
            assertTrue(runCatching { fixture.novels.permanentlyDeleteChapter(first.id) }.isFailure)

            // Move to trash and permanently delete
            fixture.novels.softDeleteChapter(first.id)
            fixture.novels.permanentlyDeleteChapter(first.id)
            assertNull(fixture.novels.chapter(first.id))
        }
    }

    @Test
    fun generationSettings_arePersistedPerNovel() = runBlocking {
        RepositoryFixture(emptyList()).use { fixture ->
            val novel = fixture.novels.createNovel("参数持久化")
            val body = GenerationSettings(streamOutput = false, temperature = 1.3, maxOutputTokens = 4096)
            val summary = GenerationSettings(streamThinking = true, thinkingEnabled = true)
            val assistant = GenerationSettings(temperature = 0.2)

            fixture.novels.updateGenerationSettings(novel.id, body, summary, assistant)

            val stored = requireNotNull(fixture.novels.novel(novel.id))
            assertEquals(body, fixture.json.decodeFromString<GenerationSettings>(requireNotNull(stored.bodyGenerationSettingsJson)))
            assertEquals(summary, fixture.json.decodeFromString<GenerationSettings>(requireNotNull(stored.summaryGenerationSettingsJson)))
            assertEquals(assistant, fixture.json.decodeFromString<GenerationSettings>(requireNotNull(stored.assistantGenerationSettingsJson)))
        }
    }

    @Test
    fun appSummaryDefault_isCopiedOnlyWhenNovelIsCreated() = runBlocking {
        RepositoryFixture(emptyList()).use { fixture ->
            val first = fixture.novels.createNovel("第一本")
            fixture.database.novelDao().updateNovel(first.copy(summaryInstruction = "以后默认指令"))
            fixture.novels.saveNovelSummaryAsAppDefault(first.id)
            val second = fixture.novels.createNovel("第二本")
            assertEquals("以后默认指令", fixture.novels.novel(second.id)?.summaryInstruction)

            fixture.database.novelDao().updateNovel(first.copy(summaryInstruction = "本作独立修改"))
            assertEquals("以后默认指令", fixture.novels.novel(second.id)?.summaryInstruction)
            fixture.novels.resetAppSummaryDefaultToFactory()
            assertEquals("以后默认指令", fixture.novels.novel(second.id)?.summaryInstruction)
        }
    }

    @Test
    fun historyCleanup_deletesOnlyUnreferencedSnapshots() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("可追溯候选"))).use { fixture ->
            val novel = fixture.configuredNovel()
            val chapter = fixture.database.novelDao().allChapters(novel.id).single()
            fixture.generation.generateBody(novel.id, chapter.id, "测试", GenerationSettings())
            val preview = fixture.novels.previewHistoryCleanup(novel.id, TaskType.BODY, null)
            assertEquals(1, preview.matchedRequests)
            assertEquals(0, preview.deletableRequests)
            assertEquals(1, preview.blockedRequests)
            val result = fixture.novels.cleanupHistory(novel.id, TaskType.BODY, null)
            assertEquals(0, result.deletedRequests)
            assertEquals(1, fixture.database.generationDao().allRequestSnapshots().size)
        }
    }
}
