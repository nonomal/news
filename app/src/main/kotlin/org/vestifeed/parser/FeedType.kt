package org.vestifeed.parser

import org.w3c.dom.Document

enum class FeedType {
    ATOM,
    RSS,
    RDF,
    UNKNOWN,
}

fun feedType(document: Document): FeedType {
    val documentElement = document.documentElement

    if (documentElement.tagName == "feed"
        && documentElement.getAttribute("xmlns") == "http://www.w3.org/2005/Atom"
    ) {
        return FeedType.ATOM
    }

    if (documentElement.tagName == "rss") {
        return FeedType.RSS
    }

    if (documentElement.tagName == "rdf:RDF") {
        return FeedType.RDF
    }

    return FeedType.UNKNOWN
}