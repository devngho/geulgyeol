package io.github.devngho.geulgyeol.site.blog

import com.vladsch.flexmark.ext.escaped.character.EscapedCharacterExtension
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import it.skrape.core.htmlDocument
import it.skrape.selects.DocElement
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * thx gpt o1 preview
 */
object Tistory {
    fun List<BlogComponent.InlineElement>.trim(): List<BlogComponent.InlineElement> {
        if (isEmpty()) return this

        if (first() is BlogComponent.TextElement && (first() as BlogComponent.TextElement).text.isBlank()) {
            return drop(1).trim()
        }

        if (last() is BlogComponent.TextElement && (last() as BlogComponent.TextElement).text.isBlank()) {
            return dropLast(1).trim()
        }

        return this
    }

    private val converter = FlexmarkHtmlConverter.builder(MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(EscapedCharacterExtension.create()))
    }).build()

    sealed class Component {
        abstract fun parse(element: DocElement): BlogComponent.ContentBlock

        data object TextComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.Paragraph {
//                if (element.tagName == "p") { // self is paragraph
                val inlineElements = parseInlineElements(element)
                return BlogComponent.Paragraph(inlineElements)
//                }

//                val elements = mutableListOf<BlogComponent.ContentBlock>()
//
//                element.let { runCatching { it.findFirst("div.se-module") }.getOrDefault(it) }.children.forEach {
//                    if (it.tagName == "p" && it.hasClass("se-text-paragraph")) {
//                        val inlineElements = parseInlineElements(it)
//                        elements.add(BlogComponent.Paragraph(inlineElements))
//                    } else if (it.tagName == "ul" || it.tagName == "ol") {
//                        val list = ListComponent.parse(it)
//                        elements.add(list)
//                    }
//                }
//
//                return if (elements.isNotEmpty()) {
//                    BlogComponent.ContentBlocks(elements)
//                } else {
//                    BlogComponent.EmptyParagraph
//                }
            }
        }

        data object LinkPreviewComponent : Component() {
            /**
             * takes figure element
             */
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val url = element.dataAttributes["data-og-url"] ?: ""
                val title = element.dataAttributes["data-og-title"] ?: ""
                val description = element.dataAttributes["data-og-description"] ?: ""
                val imageUrl = element.dataAttributes["data-og-image"] ?: ""
                return BlogComponent.LinkPreview(
                    url = url,
                    title = title,
                    description = description,
                    imageUrl = imageUrl
                )
            }
        }

        data object HorizontalLineComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                return BlogComponent.HorizontalLine
            }
        }

        data object SectionTitleComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val level = element.tagName.removePrefix("h").toIntOrNull() ?: 2

                return BlogComponent.SectionTitle(parseInlineElements(element), level)
            }
        }

        data object QuoteComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val paragraph = TextComponent.parse(element)

                return BlogComponent.Quote(content = listOf(paragraph.let {
                    if (it.elements.count() <= 1) {
                        it
                    } else {
                        BlogComponent.Paragraph(listOf(
                            it.elements.first(),
                            *(it.elements.drop(1).map { element ->
                                if (element is BlogComponent.TextElement && element.text.startsWith(" ")) {
                                    BlogComponent.TextElement(text = element.text.drop(1))
                                } else {
                                    element
                                }
                            }.toTypedArray())
                        ))
                    }
                }), citation = null)
            }
        }

        data object CodeBlockComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock = runCatching {
                val codeElement = element.findFirst("code")
                val code = codeElement.text
                val language = codeElement.parent.dataAttributes["data-ke-language"] ?: ""

                BlogComponent.CodeBlock(code = code.trim(), language = language)
            }.recoverCatching {
                // just connect all text

                val code = element.wholeText
                BlogComponent.CodeBlock(code = code.trim(), language = "")
            }.getOrThrow()
        }

        data object TableComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val rows = mutableListOf<BlogComponent.TableRow>()
                val trElements = try { element.findAll("tr") } catch (e: Exception) { return BlogComponent.HorizontalLine }

                var rowIndex = 0
                while (rowIndex < trElements.size) {
                    val tr = trElements[rowIndex]
                    val tdElements = try { tr.findAll("td, th") } catch (e: Exception) { rowIndex++; continue }
                    val cells = mutableListOf<BlogComponent.TableCell>()

                    var colIndex = 0
                    tdElements.forEach { td ->
                        val rowspan = td.attribute("rowspan").toIntOrNull() ?: 1
                        val colspan = td.attribute("colspan").toIntOrNull() ?: 1

                        val cell = BlogComponent.TableCell(content = listOf(BlogComponent.Paragraph(parseInlineElements(td))))

                        // 셀을 현재 행에 추가
                        cells.add(cell)
                        for (i in 1 until colspan) {
//                            cells.add(cell)
                            cells.add(BlogComponent.TableCell(content = emptyList()))
                        }

                        // rowspan이 2 이상인 경우 빈 셀 추가
                        if (rowspan > 1) {
                            for (r in 1 until rowspan) {
                                val targetRowIndex = rowIndex + r
                                // 필요한 경우 새로운 행을 추가
                                while (rows.size <= targetRowIndex) {
                                    rows.add(BlogComponent.TableRow(cells = mutableListOf()))
                                }
                                val targetRow = rows[targetRowIndex]
                                for (i in 0 until colspan) {
                                    (targetRow.cells as MutableList).add(BlogComponent.TableCell(content = emptyList()))
                                }
                            }
                        }

                        colIndex += colspan
                    }

                    // 현재 행에 셀 추가
                    if (rows.size > rowIndex) {
                        (rows[rowIndex].cells as MutableList).addAll(cells)
                    } else {
                        rows.add(BlogComponent.TableRow(cells = cells))
                    }

                    rowIndex++
                }

                // 모든 행의 셀 수를 동일하게 맞추기 위해 빈 셀 추가
                val maxCellCount = rows.maxOf { it.cells.size }
                rows.forEach { row ->
                    while (row.cells.size < maxCellCount) {
                        (row.cells as MutableList).add(BlogComponent.TableCell(content = emptyList()))
                    }
                }

                return BlogComponent.Table(rows = rows)
            }
        }

        data object ListComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                if (element.tagName == "ol" || element.tagName == "ul") { // self is list
                    val ordered = element.tagName == "ol"
                    val items = parseListItems(element)

                    return BlogComponent.ListBlock(items = items, ordered = ordered)
                }

                // 목록 유형 결정 (ordered or unordered)
                val ordered = kotlin.runCatching { element.findFirst("ol") }.isSuccess
                val listElement = if (ordered) element.findFirst("ol") else element.findFirst("ul")
                val items = parseListItems(listElement)
                return BlogComponent.ListBlock(items = items, ordered = ordered)
            }

            private fun parseListItems(listElement: DocElement): List<BlogComponent.ListItem> {
                val items = mutableListOf<BlogComponent.ListItem>()

                listElement.children.filter { it.tagName == "li" }.forEach { li ->
                    val contentBlocks = mutableListOf<BlogComponent.ContentBlock>()

                    val grouped = mutableListOf<List<Node>>()
                    val currentGroup = mutableListOf<Node>()

                    li.element.childNodes().forEach { child ->
                        if (child is TextNode || (child is Element && (child.tagName() != "ul" && child.tagName() != "ol"))) {
                            currentGroup.add(child)
                        } else {
                            if (currentGroup.isNotEmpty()) {
                                grouped.add(currentGroup.toList())
                                currentGroup.clear()
                            }
                            grouped.add(listOf(child))
                        }
                    }

                    if (currentGroup.isNotEmpty()) {
                        grouped.add(currentGroup.toList())
                    }

                    grouped.forEach { group ->
                        if (group.isNotEmpty()) {
                            // is list?
                            if (group.size == 1 && group.first() is Element && ((group.first() as Element).tagName() == "ul" || (group.first() as Element).tagName() == "ol")) {
                                val list = parse(htmlDocument(group.first().outerHtml()).children.first())
                                contentBlocks.add(list)
                            } else {
                                val elements = group.filterNot { (it is TextNode && it.wholeText == "\n") }
                                if (elements.isNotEmpty()) {
                                    val inlineElements =
                                        parseInlineElements(
                                            htmlDocument(
                                                "<div>" + elements.joinToString (separator =
                                                    "") { it.outerHtml() } + "</div>").findFirst("body").children.first())
                                    contentBlocks.add(BlogComponent.Paragraph(inlineElements.trim()))
                                }
                            }
                        }
                    }

                    if (contentBlocks.isNotEmpty()) {
                        items.add(BlogComponent.ListItem(content = contentBlocks))
                    }
                }

                return items
            }
        }

        data object ImageComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                return runCatching {
                    val span = element.findFirst("span")
                    val src = span.dataAttributes["data-url"]!!
                    val alt = span.dataAttributes["data-alt"]
                    val caption = runCatching { element.findFirst("figcaption").text }.getOrNull()

                    BlogComponent.Image(url = src, altText = alt, caption = caption)
                }.recoverCatching {
                    val img = element.findFirst("img")
                    val src = img.attributes["src"]!!
                    val alt = img.attributes["alt"]
                    val caption = runCatching { element.findFirst("figcaption").text }.getOrNull()

                    BlogComponent.Image(url = src, altText = alt, caption = caption)
                }.getOrThrow()
            }
        }

        data object RawComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                return BlogComponent.RawElement(element.text, converter.convert(element.outerHtml))
            }
        }
    }

    private fun parseTextWithFormula(text: String): List<BlogComponent.InlineElement> {
        var textTemp = ""
        var idx = 0
        var isFormula = false
        val elements = mutableListOf<BlogComponent.InlineElement>()

        while (idx < text.length) {
            if (text[idx] == '$' && (idx == 0 || text[idx - 1] != '\\')) {
                if (isFormula) {
                    elements.add(BlogComponent.InlineFormulaElement(latex = textTemp))
                } else {
                    elements.add(BlogComponent.TextElement(text = textTemp))
                }
                textTemp = ""
                isFormula = !isFormula
            } else {
                textTemp += text[idx]
            }
            idx++
        }

        if (textTemp.isNotEmpty()) {
            elements.add(BlogComponent.TextElement(text = textTemp))
        }

        return elements
    }

    // 인라인 요소 파싱 함수 (공통 함수)
    fun parseInlineElements(element: DocElement): List<BlogComponent.InlineElement> {
        val inlineElements = mutableListOf<BlogComponent.InlineElement>()

        element.element.childNodes().forEach { child ->
            when (child) {
                is TextNode -> {
                    val text = child.text().replace("\u200B", "")
                    inlineElements.addAll(parseTextWithFormula(text))
                }

                is Element -> {
                    if (child.tagName() == "script") {
                        return@forEach
                    }

                    val skrapeElement = htmlDocument(child.outerHtml())
                    val innerElement = skrapeElement.findFirst("body").children.first()

                    fun innerBlock() = BlogComponent.BlockElement(parseInlineElements(innerElement))

                    when (child.tagName()) {
                        "b", "strong" -> {
                            inlineElements.add(BlogComponent.BoldElement(innerBlock()))
                        }

                        "i", "em" -> {
                            inlineElements.add(BlogComponent.ItalicElement(innerBlock()))
                        }

                        "u" -> {
                            inlineElements.add(BlogComponent.UnderlineElement(innerBlock()))
                        }

                        "s", "del" -> {
                            inlineElements.add(BlogComponent.StrikethroughElement(innerBlock()))
                        }

                        "sup" -> {
                            inlineElements.add(BlogComponent.SuperscriptElement(innerBlock()))
                        }

                        "sub" -> {
                            inlineElements.add(BlogComponent.SubscriptElement(innerBlock()))
                        }

                        "a" -> {
                            val url = child.attr("href")
                            inlineElements.add(BlogComponent.HyperlinkElement(text = innerBlock(), url = url))
                        }

                        "br" -> {
                            inlineElements.add(BlogComponent.TextElement(text = "\n"))
                        }

                        "span" -> {
                            inlineElements.addAll(innerBlock().elements)
                        }

                        else -> {
                            // 기타 인라인 요소 처리
                            val text = child.text()
                            inlineElements.addAll(parseTextWithFormula(text))
                        }
                    }
                }
            }
        }

        return inlineElements
    }

    // 전체 컴포넌트 파싱 함수
    fun parseContentBlocks(doc: DocElement): List<BlogComponent.ContentBlock> {
        val contentBlocks = mutableListOf<BlogComponent.ContentBlock>()

        doc.children.forEach { component ->
            val contentBlock = when {
                component.tagName == "p" -> {
                    if (component.ownText.trim().isEmpty() && component.children.isEmpty()) {
                        null
                    } else {
                        Component.TextComponent.parse(component)
                    }
                }
                component.tagName == "figure" -> {
                    if (component.hasClass("imageblock")) {
                        Component.ImageComponent.parse(component)
                    } else if (component.dataAttributes["data-ke-type"] == "opengraph") {
                        Component.LinkPreviewComponent.parse(component)
                    } else {
                        null
                    }
                }
                component.tagName == "hr" -> Component.HorizontalLineComponent.parse(component)
                component.tagName.startsWith("h") -> Component.SectionTitleComponent.parse(component)
                component.tagName == "blockquote" -> Component.QuoteComponent.parse(component)
                component.tagName == "pre" -> Component.CodeBlockComponent.parse(component)
                component.tagName == "table" -> Component.TableComponent.parse(component)
                component.tagName == "ul" || component.tagName == "ol" -> Component.ListComponent.parse(component)
                component.tagName == "div" && component.dataAttributes["data-ke-type"] == "html" -> Component.RawComponent.parse(component)
                // 다른 컴포넌트 타입에 대한 처리 추가 가능
                else -> {
                    // 알 수 없는 컴포넌트는 무시하거나 로깅
                    null
                }
            }
            contentBlock?.let { contentBlocks.add(it) }
        }

        return contentBlocks
    }

    // 메인 파싱 함수
    fun DocElement.parseArticle(): BlogComponent.Article =
        BlogComponent.Article(contentBlocks = parseContentBlocks(this))

    @Serializable
    data class Post(
        val logNo: String,
    )

    @Serializable
    data class PostTitleList(
        val postList: List<Post>
    )
}

