package com.crtc.books

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EpubChapter(val title: String, val body: String)
data class ImportedBook(val title: String, val author: String, val chapters: List<EpubChapter>)

object EpubImporter {

    fun parse(ctx: Context, uri: Uri): ImportedBook {
        val stream = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file")
        return parseStream(stream)
    }

    fun parseStream(input: InputStream): ImportedBook {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val containerXml = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8) ?: ""
        val opfPath = Regex("""full-path="([^"]+\.opf)"""").find(containerXml)
            ?.groupValues?.get(1) ?: ""
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opfXml = entries[opfPath]?.toString(Charsets.UTF_8) ?: ""

        val title = Regex("<dc:title[^>]*>([^<]+)<").find(opfXml)?.groupValues?.get(1) ?: "Imported Book"
        val author = Regex("<dc:creator[^>]*>([^<]+)<").find(opfXml)?.groupValues?.get(1) ?: "Unknown"

        val idHref = mutableMapOf<String, String>()
        Regex("""<item\b[^>]+\bid="([^"]+)"[^>]+href="([^"]+\.x?html?)"""").findAll(opfXml).forEach {
            idHref[it.groupValues[1]] = it.groupValues[2]
        }

        val spineIds = Regex("""<itemref\s+idref="([^"]+)"""").findAll(opfXml)
            .map { it.groupValues[1] }.toList()
        val orderedHrefs = spineIds.mapNotNull { idHref[it] }

        val chapters = mutableListOf<EpubChapter>()
        for (href in orderedHrefs) {
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            val html = entries[fullPath]?.toString(Charsets.UTF_8) ?: continue
            val doc = Jsoup.parse(html)
            val chTitle = doc.selectFirst("h1,h2,h3")?.text()
                ?: doc.title().takeIf { it.isNotBlank() } ?: "Chapter"
            val body = doc.body()?.text()?.trim() ?: continue
            if (body.length > 100) chapters.add(EpubChapter(chTitle, body))
        }

        return ImportedBook(title, author, chapters)
    }

    fun totalWords(book: ImportedBook): Int =
        book.chapters.sumOf { it.body.split("\\s+".toRegex()).size }
}
