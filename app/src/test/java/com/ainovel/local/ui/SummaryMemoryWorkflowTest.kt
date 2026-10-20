package com.ainovel.local.ui

import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.SummaryStatus
import com.ainovel.local.domain.preparePermanentChapterDeletion
import com.ainovel.local.domain.prepareSummaryUpdate
import com.ainovel.local.ui.localization.AppLanguage
import com.ainovel.local.ui.localization.localized
import com.ainovel.local.ui.screens.CHAPTER_RAIL_EXPANDED_WIDTH_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SummaryMemoryWorkflowTest {

    private fun createSummary(
        id: String = "summary-1",
        novelId: String = "novel-1",
        name: String = "第1–3章总结",
        content: String = "这是总结正文。",
        contentVersion: Long = 1L,
        status: SummaryStatus = SummaryStatus.VALID,
    ): SummaryEntity = SummaryEntity(
        id = id,
        novelId = novelId,
        name = name,
        content = content,
        directSourceNodeIdsJson = "[\"ch-1\",\"ch-2\",\"ch-3\"]",
        coveredChapterIdsJson = "[\"ch-1\",\"ch-2\",\"ch-3\"]",
        sourceVersionSnapshotJson = "{\"ch-1\":1}",
        contentVersion = contentVersion,
        status = status,
        instructionSnapshot = "请生成总结",
        logicalPosition = 3000L,
    )

    @Test
    fun validateAndUpdateSummary_updatesTitleAndContent_andBumpsContentVersionOnlyWhenContentChanges() {
        val original = createSummary(name = "第1–3章总结", content = "原本的总结内容", contentVersion = 2L)

        // Case 1: Only title changes -> contentVersion stays 2L, contentChanged = false
        val titleOnlyResult = prepareSummaryUpdate(
            existing = original,
            newName = "  第一卷：荆棘姬的午睡  ",
            newContent = "原本的总结内容",
            now = 5000L,
        )
        assertEquals("第一卷：荆棘姬的午睡", titleOnlyResult.updatedSummary.name)
        assertEquals("原本的总结内容", titleOnlyResult.updatedSummary.content)
        assertEquals(2L, titleOnlyResult.updatedSummary.contentVersion)
        assertEquals(false, titleOnlyResult.contentChanged)
        assertEquals(true, titleOnlyResult.nameChanged)

        // Case 2: Both title and content change -> contentVersion increments to 3L, contentChanged = true
        val bothResult = prepareSummaryUpdate(
            existing = original,
            newName = "第一卷：苏醒",
            newContent = "  修改后的总结正文  ",
            now = 6000L,
        )
        assertEquals("第一卷：苏醒", bothResult.updatedSummary.name)
        assertEquals("修改后的总结正文", bothResult.updatedSummary.content)
        assertEquals(3L, bothResult.updatedSummary.contentVersion)
        assertEquals(true, bothResult.contentChanged)
        assertEquals(true, bothResult.nameChanged)
    }

    @Test
    fun validateAndUpdateSummary_rejectsBlankTitleOrBlankContent() {
        val original = createSummary()

        try {
            prepareSummaryUpdate(existing = original, newName = "   ", newContent = "有效正文")
            fail("Expected IllegalArgumentException for blank summary title")
        } catch (e: IllegalArgumentException) {
            assertEquals("总结标题不能为空", e.message)
        }

        try {
            prepareSummaryUpdate(existing = original, newName = "有效标题", newContent = "   ")
            fail("Expected IllegalArgumentException for blank summary content")
        } catch (e: IllegalArgumentException) {
            assertEquals("总结内容不能为空", e.message)
        }
    }

    @Test
    fun chapterRailExpandedWidth_providesEnoughHorizontalSpaceForTitleAndActions() {
        // Previously 232dp left <4dp for the title when index + 3 IconButtons were present.
        // Expanded rail width must be >= 300dp so chapter titles render horizontally without character-per-line clipping.
        assertTrue(
            "Expanded ChapterRail width ($CHAPTER_RAIL_EXPANDED_WIDTH_DP dp) must be >= 300dp",
            CHAPTER_RAIL_EXPANDED_WIDTH_DP >= 300,
        )
    }

    @Test
    fun preparePermanentChapterDeletion_allowsOnlyChaptersInTrash() {
        val trashedChapter = ChapterEntity(
            id = "ch-trashed",
            novelId = "novel-1",
            storyOrder = 1000L,
            name = "第百二十七話",
            deleted = true,
            deletedAt = 123456L,
        )
        assertEquals(trashedChapter, preparePermanentChapterDeletion(trashedChapter))

        val activeChapter = ChapterEntity(
            id = "ch-active",
            novelId = "novel-1",
            storyOrder = 2000L,
            name = "第百二十八話",
            deleted = false,
        )
        try {
            preparePermanentChapterDeletion(activeChapter)
            fail("Expected IllegalArgumentException when attempting permanent deletion on active chapter")
        } catch (e: IllegalArgumentException) {
            assertEquals("只能彻底删除回收区中的章节", e.message)
        }

        try {
            preparePermanentChapterDeletion(null)
            fail("Expected IllegalStateException when chapter does not exist")
        } catch (e: IllegalStateException) {
            assertEquals("章节不存在", e.message)
        }
    }

    @Test
    fun summaryMemoryStrings_areLocalizedInChineseAndEnglish() {
        val pairs = listOf(
            "总结标题" to "Summary title",
            "总结标题不能为空" to "Summary title cannot be empty",
            "总结内容不能为空" to "Summary content cannot be empty",
            "删除总结失败" to "Could not delete the summary",
            "总结已删除" to "Summary deleted",
            "删除后，这份总结会从故事记忆中移除；依赖它的后续总结会失效，过去的创作记录仍会保留。" to
                "This summary will be removed from story memory. Dependent summaries will become outdated, while past writing records will remain.",
            "彻底删除章节" to "Permanently delete chapter",
            "彻底删除章节？" to "Permanently delete this chapter?",
            "彻底删除" to "Delete permanently",
            "彻底删除后，该章节的正文与版本将永久移除，无法恢复。" to
                "Once permanently deleted, this chapter's text and versions will be removed forever and cannot be recovered.",
            "章节已彻底删除" to "Chapter permanently deleted",
            "彻底删除章节失败" to "Could not permanently delete the chapter",
            "只能彻底删除回收区中的章节" to "Only chapters in Trash can be permanently deleted",
        )
        for ((zh, en) in pairs) {
            assertEquals(zh, localized(zh, AppLanguage.SIMPLIFIED_CHINESE))
            assertEquals(en, localized(zh, AppLanguage.ENGLISH))
        }

        assertEquals(
            "Delete “Chapter 1 Summary”?",
            localized("删除“Chapter 1 Summary”？", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Delete Chapter 1 Summary",
            localized("删除 Chapter 1 Summary", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Could not delete the summary: Summary not found",
            localized("删除总结失败：总结不存在", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Could not permanently delete the chapter: Only chapters in Trash can be permanently deleted",
            localized("彻底删除章节失败：只能彻底删除回收区中的章节", AppLanguage.ENGLISH),
        )
    }
}
