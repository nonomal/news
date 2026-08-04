package org.vestifeed.parser

import org.w3c.dom.Document
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

sealed class Feed

private val xmlDocumentBuilder by lazy { DocumentBuilderFactory.newInstance().newDocumentBuilder() }

fun feed(inputStream: InputStream, mediaType: String): FeedResult {
    return if (
        mediaType.startsWith("application/rss+xml")
        || mediaType.startsWith("application/atom+xml")
        || mediaType.startsWith("application/xml")
        || mediaType.startsWith("text/xml")
    ) {
        return feedFromXml(inputStream)
    } else {
        FeedResult.UnsupportedMediaType(mediaType)
    }
}

private fun feedFromXml(inputStream: InputStream): FeedResult {
    val bytes = inputStream.readBytes()

    val document = runCatching {
        xmlDocumentBuilder.parse(ByteArrayInputStream(bytes))
    }.getOrElse {
        return when (it) {
            is SAXException -> recoverFromItemError(bytes) ?: FeedResult.ParserError(it)
            is IOException -> FeedResult.IOError(it)
            else -> throw IllegalStateException()
        }
    }

    return documentToFeedResult(document)
}

private fun documentToFeedResult(document: Document): FeedResult {
    return when (feedType(document)) {
        FeedType.ATOM -> {
            atomFeed(document).map {
                FeedResult.Success(it)
            }.getOrElse {
                FeedResult.ParserError(it)
            }
        }

        FeedType.RSS -> {
            rssFeed(document).map {
                FeedResult.Success(it)
            }.getOrElse {
                FeedResult.ParserError(it)
            }
        }

        FeedType.RDF -> {
            rdfFeed(document).map {
                FeedResult.Success(it)
            }.getOrElse {
                FeedResult.ParserError(it)
            }
        }

        FeedType.UNKNOWN -> FeedResult.UnsupportedFeedType
    }
}

private fun recoverFromItemError(bytes: ByteArray): FeedResult? {
    var text = bytes.toString(Charsets.UTF_8)

    while (true) {
        val result = runCatching {
            xmlDocumentBuilder.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        }

        val document = result.getOrNull()
        if (document != null) {
            return documentToFeedResult(document)
        }

        val exception = result.exceptionOrNull() as? SAXParseException ?: return null
        val failureLine = exception.lineNumber.takeIf { it > 0 } ?: return null

        val itemRanges = findItemTextRanges(text)
        if (itemRanges.isEmpty()) return null

        val badItemRange = itemRanges.find { range ->
            val startLine = lineNumberAt(text, range.first)
            val endLine = lineNumberAt(text, range.last)
            failureLine in startLine..endLine
        } ?: return null

        text = text.removeRange(badItemRange)
    }
}

private fun findItemTextRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf("<item>", i)
        if (open < 0) break
        val close = text.indexOf("</item>", open)
        if (close < 0) break
        ranges.add(open..(close + "</item>".length - 1))
        i = close + 1
    }
    return ranges
}

private fun lineNumberAt(text: String, offset: Int): Int {
    return text.substring(0, minOf(offset, text.length)).count { it == '\n' } + 1
}
