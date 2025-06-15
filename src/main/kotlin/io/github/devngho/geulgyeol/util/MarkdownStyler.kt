package io.github.devngho.geulgyeol.util

import io.github.devngho.geulgyeol.site.blog.flavor.Subscript
import io.github.devngho.geulgyeol.site.blog.flavor.Superscript
import io.github.devngho.geulgyeol.site.blog.flavor.Underline
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.common.*
import io.github.devngho.kmarkdown.flavor.gfm.GFMStrikethrough
import kotlin.reflect.KClass

private typealias Mask = Map<KClass<out MarkdownElement>, Boolean>

/**
 * merge styles in markdown elements, using raw markdown
 */
object MarkdownStyler {
    private val mergeTargets = mapOf(
        Bold::class to listOf({ block: Block -> Bold(block) }, { element: MarkdownElement -> (element as Bold).element.children }, "**", "**"),
        Italic::class to listOf({ block: Block -> Italic(block) }, { element: MarkdownElement -> (element as Italic).element.children }, "*", "*"),
        Underline::class to listOf({ block: Block -> Underline(block) }, { element: MarkdownElement -> (element as Underline).element.children }, "++", "++"),
        GFMStrikethrough::class to listOf({ block: Block -> GFMStrikethrough(block) }, { element: MarkdownElement -> (element as GFMStrikethrough).element.children }, "~~", "~~"),
        Subscript::class to listOf({ block: Block -> Subscript(block) }, { element: MarkdownElement -> (element as Subscript).element.children }, "^", "^"),
        Superscript::class to listOf({ block: Block -> Superscript(block) }, { element: MarkdownElement -> (element as Superscript).element.children }, "~", "~")
    )

    @Suppress("UNCHECKED_CAST")
    private fun flattenWithMask(element: List<MarkdownElement>, activeStyles: Mask): Pair<List<MarkdownElement>, List<Mask>> = element.map {
        if (mergeTargets.containsKey(it::class)) {
            val newStyles = activeStyles.toMutableMap()
            newStyles[it::class] = true
            val result = (mergeTargets[it::class]!![1] as (MarkdownElement) -> List<MarkdownElement>)(it).map { m -> flattenWithMask(listOf(m), newStyles) }
            Pair(result.flatMap { f -> f.first }, result.flatMap { f -> f.second })
        } else {
            Pair(listOf(it), listOf(activeStyles))
        }
    }.let {
        Pair(it.flatMap { f -> f.first }, it.flatMap { f -> f.second })
    }

    /**
     * combine repeated styles into larger ones
     * example:
     * **bold****~~boldandstrike~~** -> **bold~~boldandstrike~~**
     */
    private fun mergeList(elements: List<MarkdownElement>): List<MarkdownElement> {
        // split them into texts with mask
        val mask: Mask = mergeTargets.mapValues { false }
        val elementsMerged = flattenWithMask(elements, mask)

        // third, merge them back using raw.
        var rawText = ""

        // if mask changes, add prefix and suffix
        val previousMask = mask.toMutableMap()

        elementsMerged.first.zip(elementsMerged.second).forEach { (element, mask) ->
            var encoded = element.encode()
            if (encoded.isEmpty()) {
                return@forEach
            }

            val prefix = StringBuilder()
            val suffix = StringBuilder()
            mask.map { (type, active) ->
                if (active != previousMask[type]) {
                    if (active) {
                        prefix.append(mergeTargets[type]!![2] as String)
                    } else {
                        suffix.append(mergeTargets[type]!![3] as String)
                    }
                }
            }
            previousMask.clear()
            previousMask.putAll(mask)

            while (encoded.isNotEmpty() && encoded.first() == ' ') {
                suffix.append(" ")
                encoded = encoded.drop(1)
            }

            while (rawText.isNotEmpty() && rawText.last() == ' ') {
                suffix.append(" ")
                rawText = rawText.dropLast(1)
            }

            rawText += suffix.toString() + prefix.toString() + encoded

            Pair(element, mask)
        }

        val lastSpaces = rawText.takeLastWhile { it == ' ' }
        rawText = rawText.dropLast(lastSpaces.length)

        // add all active suffixes
        previousMask.map { (type, active) ->
            if (active) {
                rawText += (mergeTargets[type]!![3] as String)
            }
        }

        rawText += lastSpaces

        return listOf(Raw(rawText))
    }

    fun mergeBlock(element: Block): Block {
        val elementsGroup: MutableList<MutableList<MarkdownElement>> = mutableListOf()

        val nonBlockTemp = mutableListOf<MarkdownElement>()
        val flattenBlocks = flattenBlocks(element)

        for (element in flattenBlocks.children) {
            if (element is Block) {
                if (nonBlockTemp.isNotEmpty()) {
                    elementsGroup.add(nonBlockTemp)
                    nonBlockTemp.clear()
                }
                elementsGroup.add(mutableListOf(element))
            } else {
                nonBlockTemp.add(element)
            }
        }

        if (nonBlockTemp.isNotEmpty()) {
            elementsGroup.add(nonBlockTemp)
        }

        // merge non-block elements

        return Block(elementsGroup.map { list ->
            if (list.isNotEmpty()) {
                mergeList(list)
            } else {
                list
            }
        }.flatten())
    }

    fun flattenBlocks(element: Block): Block = Block(element.children.map {
        when (it) {
            is Block -> flattenBlocks(it).children
            is Bold -> listOf(Bold(Block(flattenBlocks(it.element).children)))
            is Italic -> listOf(Italic(Block(flattenBlocks(it.element).children)))
            is Underline -> listOf(Underline(Block(flattenBlocks(it.element).children)))
            is GFMStrikethrough -> listOf(GFMStrikethrough(Block(flattenBlocks(it.element).children)))
            is Subscript -> listOf(Subscript(Block(flattenBlocks(it.element).children)))
            is Superscript -> listOf(Superscript(Block(flattenBlocks(it.element).children)))
            else -> listOf(it)
        }
    }.flatten())

    fun merge(elements: List<MarkdownElement>): List<MarkdownElement> = elements.map {
        when(it) {
            is Table -> it.copy(header = it.header.copy(cols = it.header.cols.map { when (it) {
                is Table.TableCol -> it.copy(item = Block(merge(listOf(it.item))))
                is Table.TableColOrdered -> it.copy(item = Block(merge(listOf(it.item))))
                else -> it
            } }), rows = it.rows.map { row -> row.copy(cols = row.cols.map { when (it) {
                is Table.TableCol -> it.copy(item = Block(merge(listOf(it.item))))
                is Table.TableColOrdered -> it.copy(item = Block(merge(listOf(it.item))))
                else -> it
            } }) })
            is io.github.devngho.kmarkdown.flavor.common.List -> it.copy(items = merge(it.items))
            is Link -> it.copy(text = mergeBlock(it.text))
            is Paragraph -> it.copy(children = merge(it.children))
            is Heading -> it.copy(block = mergeBlock(it.block))
            is Blockquote -> it.copy(children = Paragraph(merge(it.children.children)))
            is Block -> mergeBlock(it)
            else -> {
                it
            }
        }
    }
}