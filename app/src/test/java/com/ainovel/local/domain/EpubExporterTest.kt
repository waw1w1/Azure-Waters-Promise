package com.ainovel.local.domain

import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.NovelEntity
import com.ainovel.local.ui.localization.AppLanguage
import com.ainovel.local.ui.localization.localized
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubExporterTest {

    @Test
    fun export_withValidChaptersAndCover_createsValidEpub3Archive() {
        val novel = NovelEntity(
            id = "test-novel-uuid-1234",
            name = "绀海之约 & <Promise> \"Special\"",
            coverFileName = "test-novel-uuid-1234.webp",
            updatedAt = 1718000000000L,
        )

        val chapter1 = ChapterEntity(
            id = "ch-1",
            novelId = novel.id,
            storyOrder = 1,
            name = "第一章 & 起始 <Begin>",
            content = "海浪拍打着礁石。\n\n空气中弥漫着'咸涩'与\"清新\"的气味 & 誓言。",
        )
        val chapter2 = ChapterEntity(
            id = "ch-2",
            novelId = novel.id,
            storyOrder = 2,
            name = "", // Blank name -> default formatting "第 2 章"
            content = "第二章的第一段内容。\n第二段内容。",
        )
        val blankChapter = ChapterEntity(
            id = "ch-blank",
            novelId = novel.id,
            storyOrder = 3,
            name = "空白章节",
            content = "   \n   \n",
        )
        val deletedChapter = ChapterEntity(
            id = "ch-deleted",
            novelId = novel.id,
            storyOrder = 4,
            name = "已删除章节",
            content = "这段内容不应该出现在导出的电子书中",
            deleted = true,
        )

        // Mock WebP header bytes: RIFF....WEBP
        val fakeWebpBytes = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
            1, 2, 3, 4,
        )

        val output = ByteArrayOutputStream()
        EpubExporter.export(
            novel = novel,
            chapters = listOf(chapter2, chapter1, blankChapter, deletedChapter), // un-ordered input to verify storyOrder sorting
            coverBytes = fakeWebpBytes,
            output = output,
        )

        val zipBytes = output.toByteArray()
        assertTrue("Output zip must not be empty", zipBytes.isNotEmpty())

        val entries = mutableMapOf<String, ByteArray>()
        val entryObjects = mutableListOf<ZipEntry>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryObjects.add(entry)
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }

        // 1. Verify mimetype is the first entry and uncompressed (STORED)
        assertEquals("First ZIP entry must be mimetype", "mimetype", entryObjects.first().name)
        val expectedMime = "application/epub+zip"
        val mimeContent = entries["mimetype"]?.toString(Charsets.US_ASCII)
        assertEquals(expectedMime, mimeContent)

        val expectedCrc = CRC32().apply { update(expectedMime.toByteArray(Charsets.US_ASCII)) }.value
        val mimeEntry = entryObjects.first()
        assertEquals(ZipEntry.STORED, mimeEntry.method)
        assertEquals(expectedMime.length.toLong(), mimeEntry.size)
        assertEquals(expectedMime.length.toLong(), mimeEntry.compressedSize)
        assertEquals(expectedCrc, mimeEntry.crc)

        // 2. Verify mandatory EPUB 3 structure files exist
        assertTrue("META-INF/container.xml must exist", entries.containsKey("META-INF/container.xml"))
        assertTrue("OEBPS/content.opf must exist", entries.containsKey("OEBPS/content.opf"))
        assertTrue("OEBPS/style.css must exist", entries.containsKey("OEBPS/style.css"))
        assertTrue("OEBPS/nav.xhtml must exist", entries.containsKey("OEBPS/nav.xhtml"))
        assertTrue("OEBPS/toc.ncx must exist", entries.containsKey("OEBPS/toc.ncx"))

        // 3. Verify cover files exist
        assertTrue("OEBPS/cover.webp must exist", entries.containsKey("OEBPS/cover.webp"))
        assertTrue("OEBPS/cover.xhtml must exist", entries.containsKey("OEBPS/cover.xhtml"))

        // 4. Verify chapter files
        assertTrue("OEBPS/chapter-001.xhtml must exist", entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertTrue("OEBPS/chapter-002.xhtml must exist", entries.containsKey("OEBPS/chapter-002.xhtml"))
        assertFalse("Blank chapter should not be exported", entries.containsKey("OEBPS/chapter-003.xhtml"))
        assertFalse("Deleted chapter should not be exported", entries.containsKey("OEBPS/chapter-004.xhtml"))

        // 5. Verify container.xml points to OEBPS/content.opf
        val containerXml = entries["META-INF/container.xml"]!!.toString(Charsets.UTF_8)
        assertTrue(containerXml.contains("full-path=\"OEBPS/content.opf\""))
        assertTrue(containerXml.contains("media-type=\"application/oebps-package+xml\""))

        // 6. Verify XML escaping and structure in content.opf
        val opf = entries["OEBPS/content.opf"]!!.toString(Charsets.UTF_8)
        assertTrue("OPF must escape &", opf.contains("绀海之约 &amp; &lt;Promise&gt; &quot;Special&quot;"))
        assertTrue("OPF must contain modified date", opf.contains("dcterms:modified"))
        assertFalse("Package element should not have redundant toc attribute", opf.contains("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\" toc=\"ncx\">"))
        assertTrue("Package element must define package without toc", opf.contains("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\">"))
        assertTrue("OPF manifest must have nav", opf.contains("properties=\"nav\""))
        assertTrue("OPF manifest must have cover-image", opf.contains("properties=\"cover-image\""))
        assertTrue("OPF manifest must list chapter-001", opf.contains("id=\"chapter-001\""))
        assertTrue("OPF manifest must list chapter-002", opf.contains("id=\"chapter-002\""))
        assertTrue("OPF spine must specify toc attribute", opf.contains("<spine toc=\"ncx\">"))
        assertTrue("OPF spine must reference cover", opf.contains("idref=\"cover\""))
        assertTrue("OPF spine must reference chapter-001", opf.contains("idref=\"chapter-001\""))
        assertTrue("OPF spine must reference chapter-002", opf.contains("idref=\"chapter-002\""))

        // 7. Verify nav.xhtml has table of contents
        val nav = entries["OEBPS/nav.xhtml"]!!.toString(Charsets.UTF_8)
        assertTrue("Nav must contain epub:type=\"toc\"", nav.contains("epub:type=\"toc\""))
        assertTrue("Nav must contain escaped chapter 1 title", nav.contains("第一章 &amp; 起始 &lt;Begin&gt;"))
        assertTrue("Nav must fallback to 第 2 章 for blank name", nav.contains("第 2 章"))
        assertTrue("Nav link to chapter 1", nav.contains("href=\"chapter-001.xhtml\""))
        assertTrue("Nav link to chapter 2", nav.contains("href=\"chapter-002.xhtml\""))

        // 8. Verify toc.ncx backward compatibility
        val ncx = entries["OEBPS/toc.ncx"]!!.toString(Charsets.UTF_8)
        assertTrue(ncx.contains("<text>第一章 &amp; 起始 &lt;Begin&gt;</text>"))
        assertTrue(ncx.contains("<text>第 2 章</text>"))
        assertTrue(ncx.contains("<content src=\"chapter-001.xhtml\"/>"))
        assertTrue(ncx.contains("<content src=\"chapter-002.xhtml\"/>"))

        // 9. Verify chapter 1 content and escaping
        val ch1Xhtml = entries["OEBPS/chapter-001.xhtml"]!!.toString(Charsets.UTF_8)
        assertTrue("Title in h2 must be escaped", ch1Xhtml.contains("<h2>第一章 &amp; 起始 &lt;Begin&gt;</h2>"))
        assertTrue("First paragraph present", ch1Xhtml.contains("<p>海浪拍打着礁石。</p>"))
        assertTrue(
            "Second paragraph with escaped quotes and ampersand",
            ch1Xhtml.contains("<p>空气中弥漫着&apos;咸涩&apos;与&quot;清新&quot;的气味 &amp; 誓言。</p>"),
        )
    }

    @Test
    fun export_withJpegCover_detectsJpgExtensionAndMime() {
        val novel = NovelEntity(id = "novel-jpeg", name = "JPEG封面小说")
        val chapter = ChapterEntity(id = "ch-1", novelId = novel.id, storyOrder = 1, content = "正文")
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 1)

        val output = ByteArrayOutputStream()
        EpubExporter.export(
            novel = novel,
            chapters = listOf(chapter),
            coverBytes = jpegBytes,
            output = output,
        )

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }

        assertTrue("Should have cover.jpg", entries.containsKey("OEBPS/cover.jpg"))
        val opf = entries["OEBPS/content.opf"]!!.toString(Charsets.UTF_8)
        assertTrue("OPF should have image/jpeg", opf.contains("media-type=\"image/jpeg\""))
        assertTrue("OPF should reference cover.jpg", opf.contains("href=\"cover.jpg\""))
    }

    @Test
    fun export_withPngCover_detectsPngExtensionAndMime() {
        val novel = NovelEntity(id = "novel-png", name = "PNG封面小说")
        val chapter = ChapterEntity(id = "ch-1", novelId = novel.id, storyOrder = 1, content = "正文")
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        )

        val output = ByteArrayOutputStream()
        EpubExporter.export(
            novel = novel,
            chapters = listOf(chapter),
            coverBytes = pngBytes,
            output = output,
        )

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }

        assertTrue("Should have cover.png", entries.containsKey("OEBPS/cover.png"))
        val opf = entries["OEBPS/content.opf"]!!.toString(Charsets.UTF_8)
        assertTrue("OPF should have image/png", opf.contains("media-type=\"image/png\""))
        assertTrue("OPF should reference cover.png", opf.contains("href=\"cover.png\""))
    }

    @Test
    fun export_whenNoCoverBytes_exportsWithoutCoverEntries() {
        val novel = NovelEntity(
            id = "novel-no-cover",
            name = "无封面小说",
        )
        val chapter = ChapterEntity(
            id = "ch-1",
            novelId = novel.id,
            storyOrder = 1,
            name = "独章",
            content = "正文内容",
        )

        val output = ByteArrayOutputStream()
        EpubExporter.export(
            novel = novel,
            chapters = listOf(chapter),
            coverBytes = null,
            output = output,
        )

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }

        assertFalse("Should not have cover.webp", entries.containsKey("OEBPS/cover.webp"))
        assertFalse("Should not have cover.xhtml", entries.containsKey("OEBPS/cover.xhtml"))
        val opf = entries["OEBPS/content.opf"]!!.toString(Charsets.UTF_8)
        assertFalse("OPF should not have cover-image property", opf.contains("cover-image"))
        assertFalse("OPF spine should not have cover itemref", opf.contains("idref=\"cover\""))
    }

    @Test
    fun export_whenAllChaptersBlankOrEmpty_throwsIllegalArgumentException() {
        val novel = NovelEntity(id = "blank-novel", name = "空小说")
        val blankChapter1 = ChapterEntity(id = "ch-1", novelId = novel.id, storyOrder = 1, content = "")
        val blankChapter2 = ChapterEntity(id = "ch-2", novelId = novel.id, storyOrder = 2, content = "   \n\t  ")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            EpubExporter.export(
                novel = novel,
                chapters = listOf(blankChapter1, blankChapter2),
                output = ByteArrayOutputStream(),
            )
        }
        assertEquals("还没有可导出的正文内容", exception.message)
    }

    @Test
    fun export_whenNoChapters_throwsIllegalArgumentException() {
        val novel = NovelEntity(id = "empty-novel", name = "无章节小说")
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EpubExporter.export(
                novel = novel,
                chapters = emptyList<ChapterEntity>(),
                output = ByteArrayOutputStream(),
            )
        }
        assertEquals("还没有可导出的正文内容", exception.message)
    }

    @Test
    fun export_whenOnlyDeletedChaptersHaveContent_throwsIllegalArgumentException() {
        val novel = NovelEntity(id = "deleted-novel", name = "已删除小说")
        val deletedChapter = ChapterEntity(
            id = "ch-1",
            novelId = novel.id,
            storyOrder = 1,
            content = "虽然有内容但是被删除了",
            deleted = true,
        )
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EpubExporter.export(
                novel = novel,
                chapters = listOf(deletedChapter),
                output = ByteArrayOutputStream(),
            )
        }
        assertEquals("还没有可导出的正文内容", exception.message)
    }

    @Test
    fun buildSuggestedFileName_sanitizesUnsafeCharacters() {
        assertEquals("绀海之约.epub", EpubExporter.buildSuggestedFileName("绀海之约"))
        assertEquals("My_Cool_Novel.epub", EpubExporter.buildSuggestedFileName("My/Cool\\Novel"))
        assertEquals("novel.epub", EpubExporter.buildSuggestedFileName("   "))
        assertEquals("novel.epub", EpubExporter.buildSuggestedFileName("***:::???"))
        assertEquals("AlreadyEpub.epub", EpubExporter.buildSuggestedFileName("AlreadyEpub.epub"))
        assertEquals("AlreadyUpper.EPUB", EpubExporter.buildSuggestedFileName("AlreadyUpper.EPUB"))
    }

    @Test
    fun localization_translatesAllEpubStringsInEnglish() {
        val lang = AppLanguage.ENGLISH
        assertEquals("Export EPUB", localized("导出 EPUB", lang))
        assertEquals("Export story as EPUB", localized("导出本作 EPUB", lang))
        assertEquals("EPUB ebook exported", localized("EPUB 电子书已导出", lang))
        assertEquals("Could not export EPUB", localized("导出 EPUB 失败", lang))
        assertEquals("No chapter text available to export yet", localized("还没有可导出的正文内容", lang))

        // Dynamic error message
        assertEquals(
            "Could not export EPUB: No chapter text available to export yet",
            localized("导出 EPUB 失败：还没有可导出的正文内容", lang),
        )
        assertEquals(
            "Could not export EPUB: Unknown error",
            localized("导出 EPUB 失败：未知错误", lang),
        )
    }

    @Test
    fun filterExportableChapters_returnsActiveNonBlankChaptersInStoryOrder() {
        val ch1 = ChapterEntity(id = "ch-1", novelId = "n1", storyOrder = 10, content = "第十章内容")
        val ch2 = ChapterEntity(id = "ch-2", novelId = "n1", storyOrder = 2, content = "第二章内容")
        val blank = ChapterEntity(id = "ch-blank", novelId = "n1", storyOrder = 1, content = "   \n\t")
        val deleted = ChapterEntity(id = "ch-deleted", novelId = "n1", storyOrder = 0, content = "已删除", deleted = true)

        val result = EpubExporter.filterExportableChapters(listOf(ch1, deleted, blank, ch2))

        assertEquals(2, result.size)
        assertEquals("ch-2", result[0].id)
        assertEquals("ch-1", result[1].id)
    }

    @Test
    fun filterExportableChapters_whenNoExportableChapters_throwsIllegalArgumentException() {
        val blank = ChapterEntity(id = "ch-blank", novelId = "n1", storyOrder = 1, content = "")
        val deleted = ChapterEntity(id = "ch-del", novelId = "n1", storyOrder = 2, content = "删除", deleted = true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            EpubExporter.filterExportableChapters(listOf(blank, deleted))
        }
        assertEquals("还没有可导出的正文内容", ex.message)
    }
}
