package com.ainovel.local.domain

import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.NovelEntity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubExporter {

    private data class CoverImageInfo(
        val extension: String,
        val mediaType: String,
        val bytes: ByteArray,
    )

    fun filterExportableChapters(chapters: List<ChapterEntity>): List<ChapterEntity> {
        val activeChapters = chapters
            .filter { !it.deleted && it.content.isNotBlank() }
            .sortedBy { it.storyOrder }
        require(activeChapters.isNotEmpty()) { "还没有可导出的正文内容" }
        return activeChapters
    }

    fun export(
        novel: NovelEntity,
        chapters: List<ChapterEntity>,
        coverBytes: ByteArray? = null,
        output: OutputStream,
    ) {
        val activeChapters = filterExportableChapters(chapters)

        val coverInfo = coverBytes?.takeIf { it.isNotEmpty() }?.let { detectCover(it) }

        ZipOutputStream(output).use { zip ->
            // 1. mimetype MUST be the very first entry, uncompressed with ZipEntry.STORED
            writeMimetype(zip)

            // 2. META-INF/container.xml
            writeContainerXml(zip)

            // 3. OEBPS/style.css
            writeStyleCss(zip)

            // 4. Optional cover image and cover.xhtml
            if (coverInfo != null) {
                writeCoverImage(zip, coverInfo)
                writeCoverXhtml(zip, coverInfo)
            }

            // 5. Chapter XHTML files
            activeChapters.forEachIndexed { index, chapter ->
                writeChapterXhtml(zip, index, chapter)
            }

            // 6. OEBPS/nav.xhtml (EPUB 3 Navigation Document)
            writeNavXhtml(zip, novel, activeChapters)

            // 7. OEBPS/toc.ncx (EPUB 2 NCX table of contents)
            writeTocNcx(zip, novel, activeChapters)

            // 8. OEBPS/content.opf (EPUB 3 Package Document)
            writeContentOpf(zip, novel, activeChapters, coverInfo)
        }
    }

    fun buildSuggestedFileName(novelName: String): String {
        val sanitized = novelName
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F]"), "_")
            .trim()
            .trim('.', '_')
        val base = sanitized.ifBlank { "novel" }
        return if (base.endsWith(".epub", ignoreCase = true)) base else "$base.epub"
    }

    fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun detectCover(bytes: ByteArray): CoverImageInfo {
        return when {
            // WebP: starts with "RIFF" and has "WEBP" at index 8..11
            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> {
                CoverImageInfo("webp", "image/webp", bytes)
            }
            // JPEG: FF D8 FF
            bytes.size >= 3 &&
                (bytes[0].toInt() and 0xFF) == 0xFF &&
                (bytes[1].toInt() and 0xFF) == 0xD8 &&
                (bytes[2].toInt() and 0xFF) == 0xFF -> {
                CoverImageInfo("jpg", "image/jpeg", bytes)
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            bytes.size >= 8 &&
                (bytes[0].toInt() and 0xFF) == 0x89 &&
                (bytes[1].toInt() and 0xFF) == 0x50 &&
                (bytes[2].toInt() and 0xFF) == 0x4E &&
                (bytes[3].toInt() and 0xFF) == 0x47 -> {
                CoverImageInfo("png", "image/png", bytes)
            }
            else -> CoverImageInfo("webp", "image/webp", bytes)
        }
    }

    private fun writeMimetype(zip: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(bytes) }.value
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeContainerXml(zip: ZipOutputStream) {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>""".trimIndent()
        writeEntry(zip, "META-INF/container.xml", xml.toByteArray(Charsets.UTF_8))
    }

    private fun writeStyleCss(zip: ZipOutputStream) {
        val css = """
@charset "utf-8";
body {
    margin: 5% 8%;
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
    line-height: 1.8;
    color: #222222;
    background-color: #ffffff;
}
h1, h2, h3 {
    text-align: center;
    margin-top: 1.5em;
    margin-bottom: 1.5em;
    font-weight: 600;
    line-height: 1.3;
}
p {
    text-indent: 2em;
    margin-top: 0;
    margin-bottom: 0.6em;
    text-align: justify;
}
.cover-container {
    text-align: center;
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100vh;
}
.cover-image {
    max-width: 100%;
    max-height: 100vh;
    height: auto;
    object-fit: contain;
}
""".trimIndent()
        writeEntry(zip, "OEBPS/style.css", css.toByteArray(Charsets.UTF_8))
    }

    private fun writeCoverImage(zip: ZipOutputStream, cover: CoverImageInfo) {
        writeEntry(zip, "OEBPS/cover.${cover.extension}", cover.bytes)
    }

    private fun writeCoverXhtml(zip: ZipOutputStream, cover: CoverImageInfo) {
        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">
<head>
    <meta charset="utf-8"/>
    <title>Cover</title>
    <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
    <div class="cover-container">
        <img class="cover-image" src="cover.${cover.extension}" alt="Cover"/>
    </div>
</body>
</html>""".trimIndent()
        writeEntry(zip, "OEBPS/cover.xhtml", xhtml.toByteArray(Charsets.UTF_8))
    }

    private fun formatChapterTitle(index: Int, chapter: ChapterEntity): String {
        return chapter.name?.takeIf { it.isNotBlank() } ?: "第 ${index + 1} 章"
    }

    private fun writeChapterXhtml(zip: ZipOutputStream, index: Int, chapter: ChapterEntity) {
        val chapterTitle = formatChapterTitle(index, chapter)
        val escapedTitle = escapeXml(chapterTitle)
        val paragraphsHtml = chapter.content
            .split(Regex("\r?\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "<p>${escapeXml(it)}</p>" }

        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">
<head>
    <meta charset="utf-8"/>
    <title>$escapedTitle</title>
    <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
    <h2>$escapedTitle</h2>
    $paragraphsHtml
</body>
</html>""".trimIndent()

        val fileName = "chapter-%03d.xhtml".format(index + 1)
        writeEntry(zip, "OEBPS/$fileName", xhtml.toByteArray(Charsets.UTF_8))
    }

    private fun writeNavXhtml(zip: ZipOutputStream, novel: NovelEntity, chapters: List<ChapterEntity>) {
        val escapedNovelName = escapeXml(novel.name)
        val listItems = chapters.mapIndexed { index, chapter ->
            val title = escapeXml(formatChapterTitle(index, chapter))
            val fileName = "chapter-%03d.xhtml".format(index + 1)
            "            <li><a href=\"$fileName\">$title</a></li>"
        }.joinToString("\n")

        val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">
<head>
    <meta charset="utf-8"/>
    <title>$escapedNovelName - 目录</title>
    <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>目录</h1>
        <ol>
$listItems
        </ol>
    </nav>
</body>
</html>""".trimIndent()
        writeEntry(zip, "OEBPS/nav.xhtml", xhtml.toByteArray(Charsets.UTF_8))
    }

    private fun writeTocNcx(zip: ZipOutputStream, novel: NovelEntity, chapters: List<ChapterEntity>) {
        val escapedNovelName = escapeXml(novel.name)
        val navPoints = chapters.mapIndexed { index, chapter ->
            val playOrder = index + 1
            val title = escapeXml(formatChapterTitle(index, chapter))
            val fileName = "chapter-%03d.xhtml".format(playOrder)
            """        <navPoint id="navPoint-$playOrder" playOrder="$playOrder">
            <navLabel>
                <text>$title</text>
            </navLabel>
            <content src="$fileName"/>
        </navPoint>"""
        }.joinToString("\n")

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
    <head>
        <meta name="dtb:uid" content="urn:uuid:${novel.id}"/>
        <meta name="dtb:depth" content="1"/>
        <meta name="dtb:totalPageCount" content="0"/>
        <meta name="dtb:maxPageNumber" content="0"/>
    </head>
    <docTitle>
        <text>$escapedNovelName</text>
    </docTitle>
    <navMap>
$navPoints
    </navMap>
</ncx>""".trimIndent()
        writeEntry(zip, "OEBPS/toc.ncx", xml.toByteArray(Charsets.UTF_8))
    }

    private fun writeContentOpf(
        zip: ZipOutputStream,
        novel: NovelEntity,
        chapters: List<ChapterEntity>,
        cover: CoverImageInfo?,
    ) {
        val escapedTitle = escapeXml(novel.name)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val modifiedDate = dateFormat.format(Date(if (novel.updatedAt > 0) novel.updatedAt else System.currentTimeMillis()))

        val manifestItems = buildList {
            add("""        <item id="style" href="style.css" media-type="text/css"/>""")
            add("""        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            add("""        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            if (cover != null) {
                add("""        <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>""")
                add("""        <item id="cover-image" href="cover.${cover.extension}" media-type="${cover.mediaType}" properties="cover-image"/>""")
            }
            chapters.forEachIndexed { index, _ ->
                val id = "chapter-%03d".format(index + 1)
                add("""        <item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>""")
            }
        }.joinToString("\n")

        val spineItems = buildList {
            if (cover != null) {
                add("""        <itemref idref="cover"/>""")
            }
            chapters.forEachIndexed { index, _ ->
                val id = "chapter-%03d".format(index + 1)
                add("""        <itemref idref="$id"/>""")
            }
        }.joinToString("\n")

        val coverMeta = if (cover != null) {
            "\n        <meta name=\"cover\" content=\"cover-image\"/>"
        } else {
            ""
        }

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:identifier id="pub-id">urn:uuid:${novel.id}</dc:identifier>
        <dc:title>$escapedTitle</dc:title>
        <dc:language>zh-CN</dc:language>
        <meta property="dcterms:modified">$modifiedDate</meta>$coverMeta
    </metadata>
    <manifest>
$manifestItems
    </manifest>
    <spine toc="ncx">
$spineItems
    </spine>
</package>""".trimIndent()

        writeEntry(zip, "OEBPS/content.opf", xml.toByteArray(Charsets.UTF_8))
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, data: ByteArray) {
        val entry = ZipEntry(path).apply {
            time = System.currentTimeMillis()
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }
}
