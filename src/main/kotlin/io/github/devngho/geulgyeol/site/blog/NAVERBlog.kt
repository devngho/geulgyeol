package io.github.devngho.geulgyeol.site.blog

import it.skrape.core.htmlDocument
import it.skrape.selects.DocElement
import it.skrape.selects.html5.script
import it.skrape.selects.html5.span
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * thx gpt o1 preview
 *
 * Naver blog parser for se one post
 */
object NAVERBlog {
    sealed class Component {
        abstract fun parse(element: DocElement): BlogComponent.ContentBlock

        data object TextComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                if (element.tagName == "p" && element.hasClass("se-text-paragraph")) { // self is paragraph
                    val inlineElements = parseInlineElements(element)
                    return BlogComponent.Paragraph(inlineElements)
                }

                val elements = mutableListOf<BlogComponent.ContentBlock>()

                element.let { runCatching { it.findFirst("div.se-module") }.getOrDefault(it) }.children.forEach {
                    if (it.tagName == "p" && it.hasClass("se-text-paragraph")) {
                        val inlineElements = parseInlineElements(it)
                        elements.add(BlogComponent.Paragraph(inlineElements))
                    } else if (it.tagName == "ul" || it.tagName == "ol") {
                        val list = ListComponent.parse(it)
                        elements.add(list)
                    }
                }

                return if (elements.isNotEmpty()) {
                    BlogComponent.ContentBlocks(elements)
                } else {
                    BlogComponent.EmptyParagraph
                }
            }
        }

        data object LinkPreviewComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val url = runCatching { element.findFirst("a.se-oglink-thumbnail").attribute("href") }.recoverCatching { element.findFirst(".se-oglink-info").attribute("href") }.getOrThrow()
                val title = runCatching { element.findFirst("strong.se-oglink-title").text }.getOrDefault("")
                val description = runCatching { element.findFirst("p.se-oglink-summary").text }.getOrDefault("")
                val imageUrl = runCatching { element.findFirst("img.se-oglink-thumbnail-resource").attribute("src") }.getOrNull()

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
                val elements = mutableListOf<BlogComponent.InlineElement>()
                element.findAll("p.se-text-paragraph").forEach { p ->
                    val inlineElements = parseInlineElements(p)
                    elements.addAll(inlineElements)
                }
                return BlogComponent.SectionTitle(elements)
            }
        }

        data object QuoteComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val content = mutableListOf<BlogComponent.ContentBlock>()
                val quotation = element.findFirst("blockquote.se-quotation-container")

                quotation.findAll(".se-quote").forEach { quoteElement ->
                    val paragraph = TextComponent.parse(quoteElement)
                    content.add(paragraph)
                }

                val citation = kotlin.runCatching { quotation.findFirst(".se-cite").text }.getOrNull()

                return BlogComponent.Quote(content = content, citation = citation)
            }
        }

        data object CodeBlockComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val codeElement = element.findFirst(".se-code-source")
                val code = codeElement.text
                val languageClass = codeElement.findFirst("div.__se_code_view").attribute("class")
                val language = languageClass.split(" ").find { it.startsWith("language-") }?.removePrefix("language-")
                return BlogComponent.CodeBlock(code = code.trim(), language = language)
            }
        }

        data object FormulaComponent : Component() {
            private val json = Json {
                ignoreUnknownKeys = true
            }

            override fun parse(element: DocElement): BlogComponent.ContentBlock =
                element.script(".__se_module_data") {
                    findFirst {
                        val data = attribute("data-module")
                        val html = json.parseToJsonElement(data).jsonObject["data"]!!.jsonObject["html"]!!.jsonPrimitive.content

                        htmlDocument(html) {
                            span(".mq-selectable") {
                                findFirst {
                                    BlogComponent.Formula(latex = text.let {
                                        if (it.startsWith("$")) it.drop(1) else it
                                    }.let {
                                        if (it.endsWith("$")) it.dropLast(1) else it
                                    })
                                }
                            }
                        }
                    }
                }
        }

        data object TableComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val rows = mutableListOf<BlogComponent.TableRow>()
                val trElements = element.findAll("tr.se-tr")

                var rowIndex = 0
                while (rowIndex < trElements.size) {
                    val tr = trElements[rowIndex]
                    val tdElements = try { tr.findAll("td.se-cell, th.se-cell") } catch (e: Exception) { rowIndex++; continue }
                    val cells = mutableListOf<BlogComponent.TableCell>()

                    var colIndex = 0
                    tdElements.forEach { td ->
                        val cellContent = mutableListOf<BlogComponent.ContentBlock>()
                        val cellParagraph = BlogComponent.ContentBlocks(
                            elements = td.children.map {
                                TextComponent.parse(it)
                            }
                        )
                        cellContent.add(cellParagraph)

                        val rowspan = td.attribute("rowspan").toIntOrNull() ?: 1
                        val colspan = td.attribute("colspan").toIntOrNull() ?: 1

                        val cell = BlogComponent.TableCell(content = cellContent)

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

                    // li 내부의 직접적인 자식 요소들을 처리합니다.
                    li.children.forEach { child ->
                        when (child.tagName) {
                            "ul", "ol" -> {
                                // 서브 리스트 처리
                                contentBlocks.add(parse(child))
                            }
                            else -> {
                                // 일반 텍스트나 다른 컨텐츠 블록 처리
                                val paragraph = TextComponent.parse(child) as BlogComponent.Paragraph
                                contentBlocks.add(paragraph)
                            }
                        }
                    }

                    // 서브 리스트가 없는 경우
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
                    val img = element.findFirst("img")
                    val imageUrl = img.attribute("data-lazy-src")
                    val alt = img.attribute("alt").ifEmpty { null }
                    val caption = runCatching { element.findFirst("div.se-caption").text }.getOrNull()

                    BlogComponent.Image(url = imageUrl, altText = alt, caption = caption)
                }.recoverCatching {
                    // assume that the image is a gif (video)
                    BlogComponent.Video(url = element.findFirst("video").attribute("src"), altText = null, caption = null)
                }.getOrThrow()
            }
        }

        data object ImageGroupComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val images = element.findAll(".se-module-image").map { ImageComponent.parse(it) }

                return BlogComponent.ContentBlocks(images)
            }
        }

        data object VideoComponent : Component() {
            override fun parse(element: DocElement): BlogComponent.ContentBlock {
                val video = element.findFirst("video")
                val videoUrl = video.attribute("src")
                val caption = runCatching { element.findFirst("div.se-caption").text }.getOrNull()

                return BlogComponent.Video(url = videoUrl, altText = null, caption = caption)
            }
        }
    }

    // 인라인 요소 파싱 함수 (공통 함수)
    fun parseInlineElements(element: DocElement): List<BlogComponent.InlineElement> {
        val inlineElements = mutableListOf<BlogComponent.InlineElement>()

        element.children.forEach { child ->
            when (child.tagName) {
                "span" -> {
                    val styles = mutableListOf<String>()

                    // 스타일 클래스 추출
                    var finalText = child
                    val nodes = mutableListOf<DocElement>()

                    while (finalText.ownText.isEmpty()) {
                        if (finalText.children.isEmpty()) break

                        finalText = finalText.children.first()
                        nodes.add(finalText)
                    }

                    val text = finalText.element.wholeText().replace("\u200B", "")

                    if (nodes.any { it.tagName == "b" } || nodes.any { it.tagName == "strong" }) {
                        styles.add("bold")
                    }
                    if (nodes.any { it.tagName == "i" } || nodes.any { it.tagName == "em" }) {
                        styles.add("italic")
                    }
                    if (nodes.any { it.tagName == "u" }) {
                        styles.add("underline")
                    }
                    if (nodes.any { it.tagName == "s" } || nodes.any { it.tagName == "strike" }) {
                        styles.add("strikethrough")
                    }

                    // 태그 기반 스타일
                    if (nodes.any { it.tagName == "sup" }) {
                        styles.add("superscript")
                    }
                    if (nodes.any { it.tagName == "sub" }) {
                        styles.add("subscript")
                    }

                    inlineElements.add(styles.fold(BlogComponent.TextElement(text = text) as BlogComponent.InlineElement) { acc, node ->
                        when (node) {
                            "bold" -> BlogComponent.BoldElement(acc)
                            "italic" -> BlogComponent.ItalicElement(acc)
                            "underline" -> BlogComponent.UnderlineElement(acc)
                            "strikethrough" -> BlogComponent.StrikethroughElement(acc)
                            "superscript" -> BlogComponent.SuperscriptElement(acc)
                            "subscript" -> BlogComponent.SubscriptElement(acc)
                            else -> throw IllegalStateException("Unknown style: $node")
                        }
                    })
                }
                "a" -> {
                    val url = child.attribute("href")
                    inlineElements.add(BlogComponent.HyperlinkElement(text = BlogComponent.BlockElement(parseInlineElements(child)), url = url))
                }
                else -> {
                    // 기타 인라인 요소 처리
                    val text = child.text
                    inlineElements.add(BlogComponent.TextElement(text = text))
                }
            }
        }

        return inlineElements
    }

    // 전체 컴포넌트 파싱 함수
    fun parseContentBlocks(doc: DocElement): List<BlogComponent.ContentBlock> {
        val contentBlocks = mutableListOf<BlogComponent.ContentBlock>()

        doc.findAll(".se-component").forEach { component ->
            val contentBlock = when {
                component.hasClass("se-text") -> Component.TextComponent.parse(component)
                component.hasClass("se-oglink") -> Component.LinkPreviewComponent.parse(component)
                component.hasClass("se-horizontalLine") -> Component.HorizontalLineComponent.parse(component)
                component.hasClass("se-sectionTitle") -> Component.SectionTitleComponent.parse(component)
                component.hasClass("se-quotation") -> Component.QuoteComponent.parse(component)
                component.hasClass("se-code") -> Component.CodeBlockComponent.parse(component)
                component.hasClass("se-formula") -> Component.FormulaComponent.parse(component)
                component.hasClass("se-table") -> Component.TableComponent.parse(component)
                runCatching { component.findAll(".se-section-image").isNotEmpty() }.getOrDefault(false) -> {
                    if (runCatching { component.findFirst("video").isNotPresent }.getOrDefault(true)) Component.ImageComponent.parse(component)
                    else Component.VideoComponent.parse(component)
                }
                component.hasClass("se-imageGroup") -> Component.ImageGroupComponent.parse(component)
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

