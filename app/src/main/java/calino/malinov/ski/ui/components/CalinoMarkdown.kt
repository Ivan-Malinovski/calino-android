package calino.malinov.ski.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoTypography
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as CommonMarkText
import org.commonmark.node.ThematicBreak
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser

/** Parsed CommonMark/GFM document used by the Compose renderer and JVM tests. */
data class CalinoMarkdownDocument(val blocks: List<CalinoMarkdownBlock>)

sealed interface CalinoMarkdownBlock {
    data class Paragraph(val content: List<CalinoMarkdownInline>) : CalinoMarkdownBlock
    data class Heading(val level: Int, val content: List<CalinoMarkdownInline>) : CalinoMarkdownBlock
    data class Quote(val blocks: List<CalinoMarkdownBlock>) : CalinoMarkdownBlock
    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val items: List<CalinoMarkdownListItem>,
        val tight: Boolean,
    ) : CalinoMarkdownBlock
    data class CodeBlock(val code: String, val language: String?) : CalinoMarkdownBlock
    data object ThematicBreak : CalinoMarkdownBlock
    data class Table(
        val header: CalinoMarkdownTableRow,
        val rows: List<CalinoMarkdownTableRow>,
    ) : CalinoMarkdownBlock
}

data class CalinoMarkdownListItem(
    val blocks: List<CalinoMarkdownBlock>,
    val checked: Boolean? = null,
    val taskIndex: Int? = null,
)

data class CalinoMarkdownTableRow(
    val cells: List<List<CalinoMarkdownInline>>,
    val alignments: List<CalinoMarkdownAlignment?>,
)

enum class CalinoMarkdownAlignment { Left, Center, Right }

sealed interface CalinoMarkdownInline {
    data class Text(val value: String) : CalinoMarkdownInline
    data class Strong(val content: List<CalinoMarkdownInline>) : CalinoMarkdownInline
    data class Emphasis(val content: List<CalinoMarkdownInline>) : CalinoMarkdownInline
    data class Strikethrough(val content: List<CalinoMarkdownInline>) : CalinoMarkdownInline
    data class Code(val value: String) : CalinoMarkdownInline
    data class Link(val destination: String, val content: List<CalinoMarkdownInline>) : CalinoMarkdownInline
    data class Image(val alt: String, val destination: String) : CalinoMarkdownInline
    data object LineBreak : CalinoMarkdownInline
}

private val CalinoMarkdownExtensions = listOf(
    AutolinkExtension.create(),
    StrikethroughExtension.create(),
    TablesExtension.create(),
    TaskListItemsExtension.create(),
)

private val CalinoMarkdownParser: Parser = Parser.builder()
    .extensions(CalinoMarkdownExtensions)
    .build()

/**
 * Parses raw Markdown without rendering HTML. Raw HTML is retained as text by
 * the native renderer, so event and task content cannot inject Android views.
 */
fun parseCalinoMarkdown(markdown: String): CalinoMarkdownDocument {
    val root = CalinoMarkdownParser.parse(markdown)
    return CalinoMarkdownDocument(parseBlocks(root, MarkdownParseState()))
}

private class MarkdownParseState(var nextTaskIndex: Int = 0)

private fun parseBlocks(parent: Node, state: MarkdownParseState): List<CalinoMarkdownBlock> = childrenOf(parent).flatMap { node ->
    when (node) {
        is Paragraph -> listOf(CalinoMarkdownBlock.Paragraph(parseInline(node)))
        is Heading -> listOf(CalinoMarkdownBlock.Heading(node.level, parseInline(node)))
        is BlockQuote -> listOf(CalinoMarkdownBlock.Quote(parseBlocks(node, state)))
        is BulletList -> listOf(parseList(node, state))
        is OrderedList -> listOf(parseList(node, state))
        is FencedCodeBlock -> listOf(CalinoMarkdownBlock.CodeBlock(node.literal.orEmpty(), node.info.trim().ifEmpty { null }))
        is IndentedCodeBlock -> listOf(CalinoMarkdownBlock.CodeBlock(node.literal.orEmpty(), null))
        is ThematicBreak -> listOf(CalinoMarkdownBlock.ThematicBreak)
        is TableBlock -> listOf(parseTable(node))
        is HtmlBlock -> listOf(CalinoMarkdownBlock.Paragraph(listOf(CalinoMarkdownInline.Text(node.literal.orEmpty()))))
        is LinkReferenceDefinition -> emptyList()
        is CustomBlock -> parseBlocks(node, state)
        else -> emptyList()
    }
}

private fun parseList(list: ListBlock, state: MarkdownParseState): CalinoMarkdownBlock.ListBlock =
    CalinoMarkdownBlock.ListBlock(
        ordered = list is OrderedList,
        start = (list as? OrderedList)?.startNumber ?: 1,
        items = childrenOf(list).filterIsInstance<ListItem>().map { item ->
            val marker = findTaskMarker(item)
            val taskIndex = marker?.let { state.nextTaskIndex++ }
            CalinoMarkdownListItem(
                blocks = parseBlocks(item, state),
                checked = marker?.isChecked,
                taskIndex = taskIndex,
            )
        },
        tight = list.isTight,
    )

private fun parseTable(table: TableBlock): CalinoMarkdownBlock.Table {
    val head = childrenOf(table).filterIsInstance<TableHead>().firstOrNull()
    val body = childrenOf(table).filterIsInstance<org.commonmark.ext.gfm.tables.TableBody>().firstOrNull()
    val header = head?.let(::parseTableRows)?.firstOrNull() ?: CalinoMarkdownTableRow(emptyList(), emptyList())
    val rows = body?.let(::parseTableRows).orEmpty()
    return CalinoMarkdownBlock.Table(header, rows)
}

private fun parseTableRows(container: Node): List<CalinoMarkdownTableRow> =
    childrenOf(container).filterIsInstance<TableRow>().map { row ->
        val cells = childrenOf(row).filterIsInstance<TableCell>()
        CalinoMarkdownTableRow(
            cells = cells.map { parseInline(it) },
            alignments = cells.map { cell -> cell.alignment?.toCalinoAlignment() },
        )
    }

private fun TableCell.Alignment.toCalinoAlignment(): CalinoMarkdownAlignment = when (this) {
    TableCell.Alignment.LEFT -> CalinoMarkdownAlignment.Left
    TableCell.Alignment.CENTER -> CalinoMarkdownAlignment.Center
    TableCell.Alignment.RIGHT -> CalinoMarkdownAlignment.Right
}

private fun parseInline(parent: Node): List<CalinoMarkdownInline> = childrenOf(parent).flatMap { node ->
    when (node) {
        is CommonMarkText -> listOf(CalinoMarkdownInline.Text(node.literal.orEmpty()))
        is Code -> listOf(CalinoMarkdownInline.Code(node.literal.orEmpty()))
        is StrongEmphasis -> listOf(CalinoMarkdownInline.Strong(parseInline(node)))
        is Emphasis -> listOf(CalinoMarkdownInline.Emphasis(parseInline(node)))
        is Strikethrough -> listOf(CalinoMarkdownInline.Strikethrough(parseInline(node)))
        is Link -> listOf(CalinoMarkdownInline.Link(node.destination.orEmpty(), parseInline(node)))
        is Image -> listOf(
            CalinoMarkdownInline.Image(
                alt = inlinePlainText(node).ifEmpty { "Image" },
                destination = node.destination.orEmpty(),
            ),
        )
        is SoftLineBreak, is HardLineBreak -> listOf(CalinoMarkdownInline.LineBreak)
        is HtmlInline -> listOf(CalinoMarkdownInline.Text(node.literal.orEmpty()))
        is Paragraph -> parseInline(node)
        is TaskListItemMarker -> emptyList()
        is CustomNode -> parseInline(node)
        else -> emptyList()
    }
}

private fun findTaskMarker(node: Node): TaskListItemMarker? {
    if (node is TaskListItemMarker) return node
    return childrenOf(node).firstNotNullOfOrNull(::findTaskMarker)
}

private fun inlinePlainText(node: Node): String = buildString {
    fun visit(current: Node) {
        when (current) {
            is CommonMarkText -> append(current.literal)
            is Code -> append(current.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            is HtmlInline -> append(current.literal)
        }
        childrenOf(current).forEach(::visit)
    }
    visit(node)
}.trim()

private fun childrenOf(node: Node): List<Node> = buildList {
    var child = node.firstChild
    while (child != null) {
        add(child)
        child = child.next
    }
}

@Composable
fun CalinoMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing to preview yet.",
    onTaskCheckedChange: ((taskIndex: Int, checked: Boolean) -> Unit)? = null,
) {
    if (markdown.isBlank()) {
        Text(emptyText, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3, modifier = modifier)
        return
    }
    val document = remember(markdown) { parseCalinoMarkdown(markdown) }
    if (document.blocks.isEmpty()) {
        Text(emptyText, style = CalinoTypography.bodyLarge, color = CalinoColors.Ink3, modifier = modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        document.blocks.forEachIndexed { index, block ->
            CalinoMarkdownBlockView(block, isFirst = index == 0, onTaskCheckedChange = onTaskCheckedChange)
        }
    }
}

/** Raw Markdown input with the same Write/Preview affordance used by journals. */
@Composable
fun CalinoMarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Markdown",
    placeholder: String = "Add more detail",
    minLines: Int = 4,
    maxLines: Int = 8,
    showLabel: Boolean = true,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "$label mode" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLabel) {
                Text(label, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { preview = false },
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) { Text("Write", color = if (!preview) CalinoColors.Accent else CalinoColors.Ink3) }
            TextButton(
                onClick = { preview = true },
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) { Text("Preview", color = if (preview) CalinoColors.Accent else CalinoColors.Ink3) }
        }
        if (preview) {
            CalinoMarkdown(
                markdown = value,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = (minLines * 25).dp)
                    .semantics { contentDescription = "$label, editable" },
                textStyle = CalinoTypography.bodyLarge.copy(color = CalinoColors.Ink, lineHeight = 25.sp),
                cursorBrush = SolidColor(CalinoColors.Accent),
                minLines = minLines,
                maxLines = maxLines,
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isBlank()) Text(placeholder, color = CalinoColors.Ink3, style = CalinoTypography.bodyLarge)
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun CalinoMarkdownBlockView(
    block: CalinoMarkdownBlock,
    isFirst: Boolean = false,
    onTaskCheckedChange: ((taskIndex: Int, checked: Boolean) -> Unit)? = null,
) {
    when (block) {
        is CalinoMarkdownBlock.Paragraph -> CalinoMarkdownInlineText(
            block.content,
            style = CalinoTypography.bodyLarge.copy(lineHeight = 25.sp),
        )
        is CalinoMarkdownBlock.Heading -> CalinoMarkdownInlineText(
            block.content,
            style = when (block.level) {
                1 -> CalinoTypography.headlineSmall
                2 -> CalinoTypography.titleLarge
                3 -> CalinoTypography.titleMedium
                else -> CalinoTypography.titleSmall
            },
            modifier = Modifier.padding(top = if (isFirst) 0.dp else 5.dp),
        )
        is CalinoMarkdownBlock.Quote -> Row(
            Modifier.fillMaxWidth().padding(top = if (isFirst) 0.dp else 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.padding(end = 10.dp).width(3.dp).height(28.dp).background(CalinoColors.Accent),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.blocks.forEach { CalinoMarkdownBlockView(it, onTaskCheckedChange = onTaskCheckedChange) }
            }
        }
        is CalinoMarkdownBlock.ListBlock -> MarkdownListView(block, onTaskCheckedChange)
        is CalinoMarkdownBlock.CodeBlock -> CodeBlockView(block)
        CalinoMarkdownBlock.ThematicBreak -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = CalinoColors.Line,
        )
        is CalinoMarkdownBlock.Table -> MarkdownTableView(block)
    }
}

@Composable
private fun MarkdownListView(
    block: CalinoMarkdownBlock.ListBlock,
    onTaskCheckedChange: ((taskIndex: Int, checked: Boolean) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth(),
                // Task labels and their controls share a measured centre.
                // A fixed top inset drifts as Android font scaling changes.
                verticalAlignment = if (item.checked != null) Alignment.CenterVertically else Alignment.Top,
            ) {
                when (item.checked) {
                    null -> Text(
                        if (block.ordered) "${block.start + index}." else "•",
                        style = CalinoTypography.bodyLarge,
                        color = CalinoColors.Accent,
                        modifier = Modifier.width(if (block.ordered) 28.dp else 22.dp),
                    )
                    else -> {
                        val taskIndex = item.taskIndex
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = if (taskIndex != null && onTaskCheckedChange != null) {
                                { checked -> onTaskCheckedChange(taskIndex, checked) }
                            } else {
                                null
                            },
                            enabled = onTaskCheckedChange != null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = CalinoColors.Accent,
                                uncheckedColor = CalinoColors.Ink3,
                                checkmarkColor = CalinoColors.OnAccent,
                                disabledCheckedColor = CalinoColors.Accent,
                                disabledUncheckedColor = CalinoColors.Ink3,
                                disabledIndeterminateColor = CalinoColors.Accent,
                            ),
                            modifier = Modifier.size(44.dp).padding(end = 8.dp).semantics {
                                contentDescription = if (item.checked) "Mark checklist item open" else "Mark checklist item done"
                            },
                        )
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (block.tight) 2.dp else 6.dp),
                ) {
                    item.blocks.forEach { child ->
                        CalinoMarkdownBlockView(child, onTaskCheckedChange = onTaskCheckedChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: CalinoMarkdownBlock.CodeBlock) {
    val scrollState = rememberScrollState()
    Box(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .clipAndBorder(CalinoColors.Ink.copy(alpha = .06f), CalinoColors.Panel)
            .padding(12.dp),
    ) {
        Text(
            block.code.trimEnd(),
            style = CalinoTypography.bodyMedium.copy(fontFamily = FontFamily.Monospace, lineHeight = 21.sp),
            color = CalinoColors.Ink,
            softWrap = false,
        )
    }
}

@Composable
private fun MarkdownTableView(block: CalinoMarkdownBlock.Table) {
    val scrollState = rememberScrollState()
    val rows = listOf(block.header) + block.rows
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .widthIn(min = 320.dp)
            .clipAndBorder(CalinoColors.Line, CalinoColors.Panel),
    ) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth()) {
                row.cells.forEachIndexed { cellIndex, cell ->
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        CalinoMarkdownInlineText(
                            cell,
                            style = (if (index == 0) CalinoTypography.bodyMedium.copy(fontWeight = FontWeight.Bold) else CalinoTypography.bodyMedium),
                            textAlign = row.alignments.getOrNull(cellIndex).toTextAlign(),
                        )
                    }
                }
            }
            if (index < rows.lastIndex) HorizontalDivider(color = CalinoColors.Line)
        }
    }
}

private fun CalinoMarkdownAlignment?.toTextAlign(): TextAlign = when (this) {
    CalinoMarkdownAlignment.Center -> TextAlign.Center
    CalinoMarkdownAlignment.Right -> TextAlign.End
    else -> TextAlign.Start
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalinoMarkdownInlineText(
    content: List<CalinoMarkdownInline>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val palette = CalinoColors
    val annotated = buildAnnotatedString {
        content.forEach { appendCalinoMarkdownInline(it, palette.Accent, palette.Ink, palette.Ink2) }
    }
    val uriHandler = LocalUriHandler.current
    val hasLinks = annotated.getStringAnnotations(MarkdownLinkAnnotation, 0, annotated.length).isNotEmpty()
    // ClickableText is built on BasicText and does not supply Text's
    // LocalContentColor fallback. An unspecified color therefore rendered the
    // ordinary words around a link as black in the dark palette.
    val resolvedStyle = style.copy(color = palette.Ink, textAlign = textAlign ?: style.textAlign)
    val semanticsModifier = modifier.semantics { contentDescription = annotated.text }
    if (hasLinks) {
        ClickableText(
            text = annotated,
            modifier = semanticsModifier,
            style = resolvedStyle,
            onClick = { offset ->
                annotated
                    .getStringAnnotations(MarkdownLinkAnnotation, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.let { uriHandler.openUri(it) }
            },
        )
    } else {
        Text(annotated, modifier = semanticsModifier, style = resolvedStyle)
    }
}

private const val MarkdownLinkAnnotation = "calino-markdown-link"

private fun AnnotatedString.Builder.appendCalinoMarkdownInline(
    inline: CalinoMarkdownInline,
    accent: Color,
    ink: Color,
    ink2: Color,
) {
    when (inline) {
        is CalinoMarkdownInline.Text -> append(inline.value)
        is CalinoMarkdownInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            inline.content.forEach { appendCalinoMarkdownInline(it, accent, ink, ink2) }
        }
        is CalinoMarkdownInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            inline.content.forEach { appendCalinoMarkdownInline(it, accent, ink, ink2) }
        }
        is CalinoMarkdownInline.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            inline.content.forEach { appendCalinoMarkdownInline(it, accent, ink, ink2) }
        }
        is CalinoMarkdownInline.Code -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = ink.copy(alpha = .07f),
                color = accent,
            ),
        ) { append(inline.value) }
        is CalinoMarkdownInline.Link -> {
            val safeDestination = inline.destination.takeIf(::isSafeMarkdownUri)
            if (safeDestination == null) {
                inline.content.forEach { appendCalinoMarkdownInline(it, accent, ink, ink2) }
            } else {
                pushStringAnnotation(MarkdownLinkAnnotation, safeDestination)
                withStyle(
                    SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                ) { inline.content.forEach { appendCalinoMarkdownInline(it, accent, ink, ink2) } }
                pop()
            }
        }
        is CalinoMarkdownInline.Image -> withStyle(
            SpanStyle(color = ink2, fontStyle = FontStyle.Italic),
        ) { append("[${inline.alt}]") }
        CalinoMarkdownInline.LineBreak -> append('\n')
    }
}

private fun isSafeMarkdownUri(destination: String): Boolean =
    destination.trim().lowercase().let { it.startsWith("https://") || it.startsWith("http://") || it.startsWith("mailto:") }

private val MarkdownTaskMarker = Regex("(?m)^(\\s*(?:[-+*]|\\d+[.)])\\s+)\\[([ xX])]((?=\\s)|$)")

/** Rewrites one rendered GFM task marker without touching its surrounding Markdown. */
fun toggleCalinoMarkdownTask(markdown: String, taskIndex: Int, checked: Boolean): String {
    if (taskIndex < 0) return markdown
    val match = MarkdownTaskMarker.findAll(markdown).elementAtOrNull(taskIndex) ?: return markdown
    val marker = if (checked) "[x]" else "[ ]"
    return markdown.replaceRange(match.groups[2]!!.range.first - 1, match.groups[2]!!.range.last + 2, marker)
}

private fun Modifier.clipAndBorder(borderColor: Color, backgroundColor: Color): Modifier =
    clip(RoundedCornerShape(CalinoShapes.Row))
        .background(backgroundColor)
        .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(CalinoShapes.Row))
