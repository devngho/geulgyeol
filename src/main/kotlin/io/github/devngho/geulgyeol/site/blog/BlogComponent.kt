package io.github.devngho.geulgyeol.site.blog

import io.github.devngho.geulgyeol.site.blog.flavor.Image.Companion.img
import io.github.devngho.geulgyeol.site.blog.flavor.Subscript
import io.github.devngho.geulgyeol.site.blog.flavor.Superscript
import io.github.devngho.geulgyeol.site.blog.flavor.Underline
import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.common.*
import io.github.devngho.kmarkdown.flavor.common.Block.Companion.block
import io.github.devngho.kmarkdown.flavor.common.Blockquote.Companion.blockquote
import io.github.devngho.kmarkdown.flavor.common.CodeBlock.Companion.codeblock
import io.github.devngho.kmarkdown.flavor.common.Heading.Companion.heading
import io.github.devngho.kmarkdown.flavor.common.HorizontalRule.Companion.horizontalRule
import io.github.devngho.kmarkdown.flavor.common.Link.Companion.link
import io.github.devngho.kmarkdown.flavor.common.List.Companion.list
import io.github.devngho.kmarkdown.flavor.common.Paragraph.Companion.paragraph
import io.github.devngho.kmarkdown.flavor.common.Raw.Companion.raw
import io.github.devngho.kmarkdown.flavor.common.Table.Companion.table
import io.github.devngho.kmarkdown.flavor.gfm.GFMStrikethrough
import kotlinx.serialization.Serializable

object BlogComponent {

    // 문단 내의 인라인 요소를 나타내는 sealed class
    @Serializable
    sealed class InlineElement {
        abstract fun toText(): String
        abstract fun markdown(): MarkdownElement
    }

    @Serializable
    data class TextElement(
        val text: String
    ) : InlineElement() {
        override fun toText(): String = text

        override fun markdown() = Text(text)
    }

    @Serializable
    data class HyperlinkElement(
        val text: InlineElement,
        val url: String
    ) : InlineElement() {
        override fun toText(): String = text.toText()

        override fun markdown() = Link(Block(listOf(text.markdown())), url)
    }

    @Serializable
    data class BlockElement(
        val elements: List<InlineElement>
    ) : InlineElement() {
        override fun toText(): String = elements.joinToString("") { it.toText() }

        override fun markdown() = Block(elements.map { it.markdown() })
    }

    @Serializable
    data class BoldElement(
        val element: InlineElement
    ) : InlineElement() {
        override fun toText(): String = element.toText()

        override fun markdown() = Bold(Block(listOf(element.markdown())))
    }

    @Serializable
    data class ItalicElement(
        val element: InlineElement
    ) : InlineElement() {
        override fun toText(): String = element.toText()

        override fun markdown() = Italic(Block(listOf(element.markdown())))
    }

    @Serializable
    data class StrikethroughElement(
        val element: InlineElement
    ) : InlineElement() {
        override fun toText(): String = element.toText()

        override fun markdown() = GFMStrikethrough(Block(listOf(element.markdown())))
    }

    @Serializable
    data class UnderlineElement(
        val element: InlineElement
    ) : InlineElement() {
        override fun toText(): String = element.toText()

        override fun markdown() = Underline(Block(listOf(element.markdown())))
    }

    @Serializable
    data class SuperscriptElement(
        val element: InlineElement
    ) : InlineElement() {
        override fun toText(): String = element.toText()

        override fun markdown() = Superscript(Block(listOf(element.markdown())))
    }

    @Serializable
    data class SubscriptElement(
        val element: InlineElement
    ) : InlineElement() {
        override fun toText(): String = element.toText()

        override fun markdown() = Subscript(Block(listOf(element.markdown())))
    }

    @Serializable
    data class InlineFormulaElement(
        val latex: String
    ) : InlineElement() {
        override fun toText(): String = latex

        override fun markdown() = Raw("$${latex}$")
    }

    // 콘텐츠 블록을 나타내는 sealed class
    @Serializable
    sealed class ContentBlock {
        abstract fun toText(): String
        abstract fun markdown(dsl: MarkdownDSL): Unit
        abstract fun markdownBlock(dsl: Block.BlockDSL): Unit
    }

    @Serializable
    data class Paragraph(
        val elements: List<InlineElement>
    ) : ContentBlock() {
        override fun toText(): String = elements.joinToString("") { it.toText() }

        override fun markdown(dsl: MarkdownDSL): Unit = dsl.run {
            +block { this@Paragraph.elements.forEach { this.apply { +(it.markdown()) } } }
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = dsl.run {
            elements.forEach { +it.markdown() }
        }

        fun walk(visitor: (InlineElement) -> Unit) {
            elements.forEach {
                visitor(it)

                when (it) {
                    is BlockElement -> it.elements.forEach { visitor(it) }
                    is HyperlinkElement -> visitor(it.text)
                    is BoldElement -> visitor(it.element)
                    is ItalicElement -> visitor(it.element)
                    is StrikethroughElement -> visitor(it.element)
                    is UnderlineElement -> visitor(it.element)
                    is SuperscriptElement -> visitor(it.element)
                    is SubscriptElement -> visitor(it.element)
                    else -> {}
                }
            }
        }
    }

    @Serializable
    data class ContentBlocks(
        val elements: List<ContentBlock>
    ) : ContentBlock() {
        override fun toText(): String = elements.joinToString("\n") { it.toText() }

        override fun markdown(dsl: MarkdownDSL) {
            elements.forEach { it.apply { markdown(dsl) } }
        }

        override fun markdownBlock(dsl: Block.BlockDSL) {
            elements.forEach { it.apply { markdownBlock(dsl) } }
        }
    }

    @Serializable
    data class Image(
        val url: String,
        val altText: String?,
        val caption: String?
    ) : ContentBlock() {
        override fun toText(): String = ""

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
//            +text(caption ?: altText ?: "사진")
//            +raw("![${caption ?: altText ?: "사진"}]($url)")
            +img(url, altText, caption)
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class Video(
        val url: String,
        val altText: String?,
        val caption: String?
    ) : ContentBlock() {
        override fun toText(): String = ""

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
//            +text(caption ?: altText ?: "영상")
//            +raw("![${caption ?: altText ?: "영상"}]($url)")
            +img(url, altText, caption)
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class CodeBlock(
        val code: String,
        val language: String?
    ) : ContentBlock() {
        override fun toText(): String = code

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +codeblock(code, language)
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class Formula(
        val latex: String
    ) : ContentBlock() {
        override fun toText(): String = latex

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +raw("$$${latex}$$")
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class Table(
        val rows: List<TableRow>
    ) : ContentBlock() {
        override fun toText(): String = rows.joinToString("\n") { row ->
            row.cells.joinToString(" | ") { cell ->
                cell.content.joinToString(" ") { it.toText() }
            }
        }

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +table {
                header {
                    rows.first().cells.forEach { cell ->
                        col {
                            cell.content.forEach { it.apply { markdownBlock(this@col) } }
                        }
                    }
                }
                rows.drop(1).forEach { row ->
                    row {
                        row.cells.forEach { cell ->
                            col {
                                cell.content.forEach { it.apply { markdownBlock(this@col) } }
                            }
                        }
                    }
                }
            }
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class TableRow(
        val cells: List<TableCell>
    )

    @Serializable
    data class TableCell(
        val content: List<ContentBlock>
    )

    @Serializable
    data class Quote(
        val content: List<ContentBlock>,
        val citation: String?
    ) : ContentBlock() {
        override fun toText(): String = content.joinToString("\n") { it.toText() }

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +blockquote {
                content.forEach { it.markdown(this) }
            }
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class ListBlock(
        val items: List<ListItem>,
        val ordered: Boolean
    ) : ContentBlock() {
        override fun toText(): String = items.joinToString("\n") { item ->
            item.content.joinToString("\n") { it.toText() }
        }

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +list(if (ordered) io.github.devngho.kmarkdown.flavor.common.List.ListStyle.ORDERED else io.github.devngho.kmarkdown.flavor.common.List.ListStyle.UNORDERED) {
                markdownItems(this)
            }
        }

        private fun markdownItems(dsl: io.github.devngho.kmarkdown.flavor.common.List.ListDSL) {
            items.forEach {
                it.content.forEach { content ->
                    if (content is ListBlock) {
                        dsl.list(if (ordered) io.github.devngho.kmarkdown.flavor.common.List.ListStyle.ORDERED else io.github.devngho.kmarkdown.flavor.common.List.ListStyle.UNORDERED) {
                            content.markdownItems(this)
                        }
                    } else {
                        dsl.item {
                            content.markdownBlock(this)
                        }
                    }
                }
            }
        }

        override fun markdownBlock(dsl: Block.BlockDSL)  {
            items.forEachIndexed { index, it ->
                val prefix = if (ordered) "${index + 1}. " else "- "
                val suffix = if (index == items.size - 1) "" else "<br />"

                dsl.run { +prefix }
                it.content.forEach { it.apply { markdownBlock(dsl) } }
                dsl.run { +suffix }
            }
        }
    }

    @Serializable
    data class ListItem(
        val content: List<ContentBlock>
    )

    @Serializable
    data object HorizontalLine : ContentBlock() {
        override fun toText(): String = "---"

        override fun markdown(dsl: MarkdownDSL) = dsl.run { +horizontalRule() }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class SectionTitle(
        val content: List<InlineElement>,
        val level: Int = 2
    ) : ContentBlock() {
        override fun toText(): String = content.joinToString("") { it.toText() }

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +heading(level, {
                content.forEach { +it.markdown() }
            }) {}
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data object EmptyParagraph : ContentBlock() {
        override fun toText(): String = ""

        override fun markdown(dsl: MarkdownDSL) {}

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class LinkPreview(
        val url: String,
        val title: String,
        val description: String,
        val imageUrl: String?
    ) : ContentBlock() {
        override fun toText(): String = "$title($url)"

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +paragraph {
                +link(url) { +title }
//                +text(description)
            }
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = throw NotImplementedError()
    }

    @Serializable
    data class RawElement(
        val text: String,
        val markdown: String
    ) : ContentBlock() {
        override fun toText(): String = text

        override fun markdown(dsl: MarkdownDSL) = dsl.run {
            +raw(markdown)
        }

        override fun markdownBlock(dsl: Block.BlockDSL) = dsl.run {
            +raw(markdown)
        }
    }

    // 전체 글을 나타내는 클래스
    @Serializable
    data class Article(
        val contentBlocks: List<ContentBlock>
    ) {
        val links by lazy {
            val links = mutableListOf<String>()
            walk {
                if (it is LinkPreview) {
                    links.add(it.url)
                }

                if (it is Paragraph) {
                    it.walk {
                        if (it is HyperlinkElement) {
                            links.add(it.url)
                        }
                    }
                }
            }

            links
        }

        fun toText(): String = contentBlocks.joinToString("\n") { it.toText().trim() }

        fun MarkdownDSL.markdown() {
            contentBlocks.forEach { it.markdown(this) }
        }

        fun walk(visitor: (ContentBlock) -> Unit) {
            contentBlocks.forEach {
                visitor(it)
                when (it) {
                    is ContentBlocks -> it.elements.forEach { visitor(it) }
                    is Quote -> it.content.forEach { visitor(it) }
                    is ListBlock -> it.items.forEach { it.content.forEach { visitor(it) } }
                    is Table -> it.rows.forEach { it.cells.forEach { it.content.forEach { visitor(it) } } }
                    else -> {}
                }
            }
        }
    }
}