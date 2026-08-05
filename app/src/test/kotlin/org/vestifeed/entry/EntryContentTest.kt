package org.vestifeed.entry

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryContentTest {

    @Test
    fun keepsMarkupWithoutCodeBlocksTogether() {
        val content = "<p>Before</p><p>After</p>"

        assertEquals(
            listOf(EntryContentBlock.Markup(content)),
            splitEntryContent(content),
        )
    }

    @Test
    fun separatesAndDecodesPreformattedBlocks() {
        val content = "<p>Before</p><pre><code>val snake = listOf(&lt;Head&gt;)\n    println(&quot;move&quot;)</code></pre><p>After</p>"

        assertEquals(
            listOf(
                EntryContentBlock.Markup("<p>Before</p>"),
                EntryContentBlock.Preformatted("val snake = listOf(<Head>)\n    println(\"move\")"),
                EntryContentBlock.Markup("<p>After</p>"),
            ),
            splitEntryContent(content),
        )
    }

    @Test
    fun separatesMultipleCaseInsensitivePreformattedBlocks() {
        val content = "<PRE>first</PRE><p>Middle</p><pre class=\"code\">second</pre>"

        assertEquals(
            listOf(
                EntryContentBlock.Preformatted("first"),
                EntryContentBlock.Markup("<p>Middle</p>"),
                EntryContentBlock.Preformatted("second"),
            ),
            splitEntryContent(content),
        )
    }

    @Test
    fun findsHeadingsWithIdsAcrossHeadingLevels() {
        val html = """
            <h1 id="title">Title</h1>
            <p>Intro</p>
            <h2 id="section-a">Section A</h2>
            <h3 id="subsection">Subsection</h3>
            <h4 id="deep">Deep Heading</h4>
            <p>More text</p>
        """.trimIndent()

        assertEquals(
            listOf(
                HeadingReference("title", "Title"),
                HeadingReference("section-a", "Section A"),
                HeadingReference("subsection", "Subsection"),
                HeadingReference("deep", "Deep Heading"),
            ),
            findHeadingReferences(html),
        )
    }

    @Test
    fun skipsHeadingsWithoutIds() {
        val html = """
            <h2>No ID here</h2>
            <h2 id="only-this">Only This</h2>
        """.trimIndent()

        assertEquals(
            listOf(HeadingReference("only-this", "Only This")),
            findHeadingReferences(html),
        )
    }

    @Test
    fun skipsHeadingsWithEmptyText() {
        val html = """
            <h2 id="empty"></h2>
            <h2 id="whitespace">   </h2>
            <h2 id="real">Real heading</h2>
        """.trimIndent()

        assertEquals(
            listOf(HeadingReference("real", "Real heading")),
            findHeadingReferences(html),
        )
    }

    @Test
    fun preservesTocAnchorStructure() {
        val html = """
            <div class="table-of-contents">
                <ul>
                    <li><a href="#preface">Preface</a></li>
                    <li><a href="#conclusion">Conclusion</a></li>
                </ul>
            </div>
            <h2 id="preface">Preface</h2>
            <p>Body text.</p>
            <h2 id="conclusion">Conclusion</h2>
            <p>Wrap up.</p>
        """.trimIndent()

        assertEquals(
            listOf(
                HeadingReference("preface", "Preface"),
                HeadingReference("conclusion", "Conclusion"),
            ),
            findHeadingReferences(html),
        )
    }
}
