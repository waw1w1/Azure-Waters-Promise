package com.ainovel.local.ui

import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.ui.localization.AppLanguage
import com.ainovel.local.ui.localization.localized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssistantIdeaWorkflowTest {

    // --- Helper logic matching AppViewModel operations ---

    private fun buildRequirementApplication(
        requirementText: String,
        alsoAddToNovelWorldbook: Boolean = false,
        worldbookName: String = "",
        selectedNovelId: String? = null,
    ): Pair<ContextEntryEntity?, String> {
        require(requirementText.trim().isNotEmpty()) { "请输入本次创作要求" }
        val entry = if (alsoAddToNovelWorldbook) {
            require(worldbookName.trim().isNotEmpty()) { "请输入世界书条目名称" }
            val novelId = requireNotNull(selectedNovelId) { "请先选择作品" }
            ContextEntryEntity(
                type = EntryType.WORLDBOOK,
                scope = EntryScope.NOVEL,
                novelId = novelId,
                name = worldbookName.trim(),
                content = requirementText.trim(),
                enabled = true,
                sortOrder = System.currentTimeMillis(),
            )
        } else {
            null
        }
        val infoMessage = if (alsoAddToNovelWorldbook) {
            "已填入本章创作要求，并同步加入本作世界书"
        } else {
            "已填入本章创作要求"
        }
        return entry to infoMessage
    }

    // --- Entry construction & workflow tests ---

    @Test
    fun testSaveAssistantIdeaToGlobalForcesDisabledAndNullNovelId() {
        val entry = buildAssistantIdeaEntry(
            name = "  海风中的低语  ",
            content = "  主角在港口听到了古老的誓约…  ",
            scope = EntryScope.GLOBAL,
            type = EntryType.WORLDBOOK,
            novelId = "novel-123",
            enabledForNovel = true,
        )
        val msg = buildAssistantIdeaInfoMessage(
            scope = EntryScope.GLOBAL,
            type = EntryType.WORLDBOOK,
            enabledForNovel = true,
        )
        assertEquals("海风中的低语", entry.name)
        assertEquals("主角在港口听到了古老的誓约…", entry.content)
        assertEquals(EntryScope.GLOBAL, entry.scope)
        assertEquals(EntryType.WORLDBOOK, entry.type)
        assertNull(entry.novelId)
        assertFalse(entry.enabled)
        assertEquals("已收入灵感素材库，可在“记忆 · 灵感素材”中查看", msg)
    }

    @Test
    fun testSaveAssistantIdeaToNovelEnabledWorldbook() {
        val entry = buildAssistantIdeaEntry(
            name = "深海神殿",
            content = "神殿位于海底三千米处。",
            scope = EntryScope.NOVEL,
            type = EntryType.WORLDBOOK,
            novelId = "novel-123",
            enabledForNovel = true,
        )
        val msg = buildAssistantIdeaInfoMessage(
            scope = EntryScope.NOVEL,
            type = EntryType.WORLDBOOK,
            enabledForNovel = true,
        )
        assertEquals("深海神殿", entry.name)
        assertEquals("神殿位于海底三千米处。", entry.content)
        assertEquals(EntryScope.NOVEL, entry.scope)
        assertEquals(EntryType.WORLDBOOK, entry.type)
        assertEquals("novel-123", entry.novelId)
        assertTrue(entry.enabled)
        assertEquals("已加入本作世界书，将陪伴下一次创作", msg)
    }

    @Test
    fun testSaveAssistantIdeaToNovelEnabledPreset() {
        val entry = buildAssistantIdeaEntry(
            name = "叙事口吻预设",
            content = "用克制且充满诗意的文字叙述。",
            scope = EntryScope.NOVEL,
            type = EntryType.PRESET,
            novelId = "novel-123",
            enabledForNovel = true,
        )
        val msg = buildAssistantIdeaInfoMessage(
            scope = EntryScope.NOVEL,
            type = EntryType.PRESET,
            enabledForNovel = true,
        )
        assertEquals("叙事口吻预设", entry.name)
        assertEquals(EntryScope.NOVEL, entry.scope)
        assertEquals(EntryType.PRESET, entry.type)
        assertEquals("novel-123", entry.novelId)
        assertTrue(entry.enabled)
        assertEquals("已加入本作预设，将陪伴下一次创作", msg)
    }

    @Test
    fun testSaveAssistantIdeaToNovelDisabled() {
        val entry = buildAssistantIdeaEntry(
            name = "暂存伏笔",
            content = "可能在第三卷才用到的设定。",
            scope = EntryScope.NOVEL,
            type = EntryType.WORLDBOOK,
            novelId = "novel-123",
            enabledForNovel = false,
        )
        val msg = buildAssistantIdeaInfoMessage(
            scope = EntryScope.NOVEL,
            type = EntryType.WORLDBOOK,
            enabledForNovel = false,
        )
        assertEquals(EntryScope.NOVEL, entry.scope)
        assertEquals("novel-123", entry.novelId)
        assertFalse(entry.enabled)
        assertEquals("已存入本作，暂未启用", msg)
    }

    @Test
    fun testSaveAssistantIdeaValidationErrors() {
        try {
            buildAssistantIdeaEntry(
                name = "   ",
                content = "有内容",
                scope = EntryScope.GLOBAL,
            )
            fail("Expected exception for blank name")
        } catch (e: IllegalArgumentException) {
            assertEquals("请输入给自己看的名字", e.message)
        }

        try {
            buildAssistantIdeaEntry(
                name = "有名字",
                content = "   ",
                scope = EntryScope.GLOBAL,
            )
            fail("Expected exception for blank content")
        } catch (e: IllegalArgumentException) {
            assertEquals("请输入灵感内容", e.message)
        }

        try {
            buildAssistantIdeaEntry(
                name = "有名字",
                content = "有内容",
                scope = EntryScope.NOVEL,
                novelId = null,
            )
            fail("Expected exception for null novelId on NOVEL scope")
        } catch (e: IllegalArgumentException) {
            assertEquals("请先选择作品", e.message)
        }
    }

    @Test
    fun testApplyAssistantIdeaAsRequirementOnly() {
        val (entry, msg) = buildRequirementApplication(
            requirementText = "  写主角在灯塔上的独白  ",
            alsoAddToNovelWorldbook = false,
            selectedNovelId = "novel-123",
        )
        assertNull(entry)
        assertEquals("已填入本章创作要求", msg)
    }

    @Test
    fun testApplyAssistantIdeaAsRequirementWithNovelWorldbook() {
        val (entry, msg) = buildRequirementApplication(
            requirementText = "写主角在灯塔上的独白",
            alsoAddToNovelWorldbook = true,
            worldbookName = "下一章灵感 #01",
            selectedNovelId = "novel-123",
        )
        assertNotNull(entry)
        assertEquals("下一章灵感 #01", entry?.name)
        assertEquals("写主角在灯塔上的独白", entry?.content)
        assertEquals(EntryScope.NOVEL, entry?.scope)
        assertEquals(EntryType.WORLDBOOK, entry?.type)
        assertEquals("novel-123", entry?.novelId)
        assertTrue(entry?.enabled == true)
        assertEquals("已填入本章创作要求，并同步加入本作世界书", msg)
    }

    @Test
    fun testApplyAssistantIdeaAsRequirementValidationErrors() {
        try {
            buildRequirementApplication(
                requirementText = "   ",
                alsoAddToNovelWorldbook = false,
            )
            fail("Expected exception for blank requirementText")
        } catch (e: IllegalArgumentException) {
            assertEquals("请输入本次创作要求", e.message)
        }

        try {
            buildRequirementApplication(
                requirementText = "有要求",
                alsoAddToNovelWorldbook = true,
                worldbookName = "   ",
                selectedNovelId = "novel-123",
            )
            fail("Expected exception for blank worldbookName")
        } catch (e: IllegalArgumentException) {
            assertEquals("请输入世界书条目名称", e.message)
        }

        try {
            buildRequirementApplication(
                requirementText = "有要求",
                alsoAddToNovelWorldbook = true,
                worldbookName = "有名字",
                selectedNovelId = null,
            )
            fail("Expected exception for null novelId")
        } catch (e: IllegalArgumentException) {
            assertEquals("请先选择作品", e.message)
        }
    }

    // --- Localization tests for Simplified Chinese & English ---

    @Test
    fun testStaticStringsLocalizationInChinese() {
        val strings = listOf(
            "灵感内容已复制到剪贴板",
            "保存灵感失败",
            "请输入给自己看的名字",
            "请输入灵感内容",
            "请先选择作品",
            "已收入灵感素材库，可在“记忆 · 灵感素材”中查看",
            "已加入本作世界书，将陪伴下一次创作",
            "已加入本作预设，将陪伴下一次创作",
            "已存入本作，暂未启用",
            "填入本章要求失败",
            "请输入本次创作要求",
            "请输入世界书条目名称",
            "已填入本章创作要求，并同步加入本作世界书",
            "已填入本章创作要求",
            "复制",
            "收入灵感库",
            "用作本章要求",
            "收入灵感资料库",
            "经你确认后才会保存。你可以先删减或修改内容，再选择存放到全局灵感素材库或直接加入本作世界书。",
            "灵感素材库（全局）",
            "本作世界书（直接用于下一章）",
            "灵感内容（可编辑裁剪）",
            "确认收入",
            "用作本章创作要求",
            "确认后会将以下内容填入写作页的“本次创作要求”，并可同时加入本作世界书陪伴下一章创作。",
            "本次创作要求（可编辑裁剪）",
            "同时加入本作世界书",
            "世界书条目名称",
            "确认并前往写作",
        )
        for (str in strings) {
            assertEquals(str, localized(str, AppLanguage.SIMPLIFIED_CHINESE))
        }
    }

    @Test
    fun testStaticStringsLocalizationInEnglish() {
        assertEquals("Idea copied to clipboard", localized("灵感内容已复制到剪贴板", AppLanguage.ENGLISH))
        assertEquals("Could not save idea", localized("保存灵感失败", AppLanguage.ENGLISH))
        assertEquals("Please enter a private name", localized("请输入给自己看的名字", AppLanguage.ENGLISH))
        assertEquals("Please enter idea content", localized("请输入灵感内容", AppLanguage.ENGLISH))
        assertEquals("Please select a story first", localized("请先选择作品", AppLanguage.ENGLISH))
        assertEquals("Saved to idea library. View in “Memory · Ideas”", localized("已收入灵感素材库，可在“记忆 · 灵感素材”中查看", AppLanguage.ENGLISH))
        assertEquals("Added to story world book; will accompany the next generation", localized("已加入本作世界书，将陪伴下一次创作", AppLanguage.ENGLISH))
        assertEquals("Added to story presets; will accompany the next generation", localized("已加入本作预设，将陪伴下一次创作", AppLanguage.ENGLISH))
        assertEquals("Saved to story, currently disabled", localized("已存入本作，暂未启用", AppLanguage.ENGLISH))
        assertEquals("Could not apply chapter requirement", localized("填入本章要求失败", AppLanguage.ENGLISH))
        assertEquals("Please enter a writing request", localized("请输入本次创作要求", AppLanguage.ENGLISH))
        assertEquals("Please enter a world book entry name", localized("请输入世界书条目名称", AppLanguage.ENGLISH))
        assertEquals("Applied as chapter writing request and added to story world book", localized("已填入本章创作要求，并同步加入本作世界书", AppLanguage.ENGLISH))
        assertEquals("Applied as chapter writing request", localized("已填入本章创作要求", AppLanguage.ENGLISH))
        assertEquals("Copy", localized("复制", AppLanguage.ENGLISH))
        assertEquals("Save to idea library", localized("收入灵感库", AppLanguage.ENGLISH))
        assertEquals("Use as chapter request", localized("用作本章要求", AppLanguage.ENGLISH))
        assertEquals("Save to Idea Library", localized("收入灵感资料库", AppLanguage.ENGLISH))
        assertEquals(
            "Saved only after your confirmation. You can edit or trim content before saving to the global idea library or adding directly to this story's world book.",
            localized("经你确认后才会保存。你可以先删减或修改内容，再选择存放到全局灵感素材库或直接加入本作世界书。", AppLanguage.ENGLISH),
        )
        assertEquals("Idea library (Global)", localized("灵感素材库（全局）", AppLanguage.ENGLISH))
        assertEquals("Story world book (For next chapter)", localized("本作世界书（直接用于下一章）", AppLanguage.ENGLISH))
        assertEquals("Idea content (editable)", localized("灵感内容（可编辑裁剪）", AppLanguage.ENGLISH))
        assertEquals("Confirm and save", localized("确认收入", AppLanguage.ENGLISH))
        assertEquals("Use as Chapter Request", localized("用作本章创作要求", AppLanguage.ENGLISH))
        assertEquals(
            "After confirmation, this will be filled into Writing Request on the write tab, and can optionally join the story world book for the next chapter.",
            localized("确认后会将以下内容填入写作页的“本次创作要求”，并可同时加入本作世界书陪伴下一章创作。", AppLanguage.ENGLISH),
        )
        assertEquals("Writing request (editable)", localized("本次创作要求（可编辑裁剪）", AppLanguage.ENGLISH))
        assertEquals("Also add to story world book", localized("同时加入本作世界书", AppLanguage.ENGLISH))
        assertEquals("World book entry name", localized("世界书条目名称", AppLanguage.ENGLISH))
        assertEquals("Confirm and start writing", localized("确认并前往写作", AppLanguage.ENGLISH))
    }

    @Test
    fun testDynamicStringsLocalization() {
        // Idea and chapter numbering patterns
        assertEquals("Idea record #01", localized("灵感记录 #01", AppLanguage.ENGLISH))
        assertEquals("Idea record #09", localized("灵感记录 #09", AppLanguage.ENGLISH))
        assertEquals("Idea record #100", localized("灵感记录 #100", AppLanguage.ENGLISH))
        assertEquals("Next chapter idea #01", localized("下一章灵感 #01", AppLanguage.ENGLISH))
        assertEquals("Next chapter idea #05", localized("下一章灵感 #05", AppLanguage.ENGLISH))

        // Dynamic error message translations (prefix: reason)
        assertEquals(
            "Could not save idea: Please enter a private name",
            localized("保存灵感失败：请输入给自己看的名字", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Could not save idea: Please enter idea content",
            localized("保存灵感失败：请输入灵感内容", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Could not save idea: Please select a story first",
            localized("保存灵感失败：请先选择作品", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Could not apply chapter requirement: Please enter a writing request",
            localized("填入本章要求失败：请输入本次创作要求", AppLanguage.ENGLISH),
        )
        assertEquals(
            "Could not apply chapter requirement: Please enter a world book entry name",
            localized("填入本章要求失败：请输入世界书条目名称", AppLanguage.ENGLISH),
        )
    }
}
