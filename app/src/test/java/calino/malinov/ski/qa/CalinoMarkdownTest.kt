package calino.malinov.ski.qa

import calino.malinov.ski.ui.components.CalinoMarkdownBlock
import calino.malinov.ski.ui.components.CalinoMarkdownAlignment
import calino.malinov.ski.ui.components.CalinoMarkdownInline
import calino.malinov.ski.ui.components.CalinoMarkdownListItem
import calino.malinov.ski.ui.components.parseCalinoMarkdown
import calino.malinov.ski.ui.components.toggleCalinoMarkdownTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalinoMarkdownTest {
    @Test
    fun parsesCommonMarkInlineFormattingAndLinks() {
        val document = parseCalinoMarkdown(
            "# Heading\n\n**bold** and *italic* with ~~removed~~, `code`, and [a link](https://example.com).",
        )

        assertTrue(document.blocks[0] is CalinoMarkdownBlock.Heading)
        val paragraph = document.blocks[1] as CalinoMarkdownBlock.Paragraph
        assertTrue(paragraph.content.any { it is CalinoMarkdownInline.Strong })
        assertTrue(paragraph.content.any { it is CalinoMarkdownInline.Emphasis })
        assertTrue(paragraph.content.any { it is CalinoMarkdownInline.Strikethrough })
        assertTrue(paragraph.content.any { it is CalinoMarkdownInline.Code })
        assertTrue(paragraph.content.any { it is CalinoMarkdownInline.Link })
    }

    @Test
    fun parsesGfmTaskListsAndTables() {
        val document = parseCalinoMarkdown(
            "- [x] Done\n- [ ] Open\n\n| Name | Value |\n| :--- | ---: |\n| One | 1 |",
        )

        val list = document.blocks[0] as CalinoMarkdownBlock.ListBlock
        assertEquals(
            listOf(true, false),
            list.items.map(CalinoMarkdownListItem::checked),
        )
        assertEquals(listOf(0, 1), list.items.map(CalinoMarkdownListItem::taskIndex))
        val table = document.blocks[1] as CalinoMarkdownBlock.Table
        assertEquals(2, table.header.cells.size)
        assertEquals(1, table.rows.size)
        assertEquals(
            listOf(CalinoMarkdownAlignment.Left, CalinoMarkdownAlignment.Right),
            table.header.alignments,
        )
    }

    @Test
    fun togglesOnlyTheSelectedTaskMarkerAndPreservesMarkdown() {
        val source = "Intro [x] text\n- [ ] Milk\n  - [x] Coffee\n1. [ ] Bread"

        assertEquals(
            "Intro [x] text\n- [ ] Milk\n  - [ ] Coffee\n1. [ ] Bread",
            toggleCalinoMarkdownTask(source, taskIndex = 1, checked = false),
        )
        assertEquals(source, toggleCalinoMarkdownTask(source, taskIndex = 8, checked = true))
    }

    @Test
    fun retainsRawHtmlAsTextInsteadOfCreatingNativeContent() {
        val document = parseCalinoMarkdown("<b>safe text</b>")
        val paragraph = document.blocks.single() as CalinoMarkdownBlock.Paragraph

        assertEquals(
            "<b>safe text</b>",
            paragraph.content.joinToString(separator = "") { (it as CalinoMarkdownInline.Text).value },
        )
    }
}
