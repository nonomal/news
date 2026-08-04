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
}
