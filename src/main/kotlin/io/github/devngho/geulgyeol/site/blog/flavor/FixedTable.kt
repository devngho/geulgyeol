package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.github.devngho.kmarkdown.builder.MarkdownDSLMarker
import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.Flavor.Companion.convertTo
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor
import io.github.devngho.kmarkdown.flavor.common.Block
import io.github.devngho.kmarkdown.flavor.common.CommonFlavor
import io.github.devngho.kmarkdown.flavor.common.Table
import kotlin.collections.List as KList

@MarkdownDSLMarker
data class FixedTable(val header: Table.TableRow, val rows: KList<Table.TableRow>): MarkdownElement by Table(header, rows) {
    override val descriptor: MarkdownElementDescriptor<out MarkdownElement> = FixedTable

    override fun encode(): String {
        val headerText = header.cols.joinToString("|") { when(it) {
            is Table.TableCol -> it.item.encode().replace("\n", "")
            is Table.TableColOrdered -> it.item.encode().replace("\n", "")
            else -> throw IllegalArgumentException("Invalid table col type")
        } }
        val separatorText = header.cols.joinToString("|") {
            when (it) {
                is Table.TableCol -> "---"
                is Table.TableColOrdered -> when (it.order) {
                    Table.TableColOrder.LEFT -> ":---"
                    Table.TableColOrder.CENTER -> ":---:"
                    Table.TableColOrder.RIGHT -> "---:"
                    Table.TableColOrder.NONE -> "---"
                }

                else -> throw IllegalArgumentException("Invalid table col type")
            }
        }

        val bodyText = rows.joinToString("\n") { row ->
            "|" + row.cols.joinToString("|") { when(it) {
                is Table.TableCol -> it.item.encode().replace("\n", "")
                is Table.TableColOrdered -> it.item.encode().replace("\n", "")
                else -> throw IllegalArgumentException("Invalid table col type")
            } } + "|"
        }

        return "|$headerText|\n|$separatorText|\n$bodyText"
    }

    companion object: MarkdownElementDescriptor<FixedTable> {
        override val id: String = "table"
        override val flavor: Flavor = CommonFlavor

        override fun convertToFlavor(element: FixedTable, flavor: Flavor): MarkdownElement {
            return FixedTable(element.header.convertTo(flavor) as Table.TableRow, element.rows.map { it.convertTo(flavor) as Table.TableRow })
        }

        override fun convertFromElement(element: MarkdownElement): FixedTable? = when (element) {
            is Table -> FixedTable(element.header, element.rows)
            else -> null
        }
    }
}