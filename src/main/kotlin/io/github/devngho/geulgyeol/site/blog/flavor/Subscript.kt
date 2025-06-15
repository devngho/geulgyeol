package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.github.devngho.kmarkdown.builder.MarkdownDSLMarker
import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor
import io.github.devngho.kmarkdown.flavor.common.Block

@MarkdownDSLMarker
data class Subscript(var element: Block): MarkdownElement {
    override val descriptor: MarkdownElementDescriptor<out MarkdownElement> = Subscript

    override fun encode(): String = "~${element.encode()}~"

    companion object: MarkdownElementDescriptor<Subscript> {
        fun MarkdownDSL.sub(block: Block.BlockDSL.() -> Unit) = Subscript(Block(Block.BlockDSL(this.flavor).apply(block).build()))
        fun Block.BlockDSL.sub(block: Block.BlockDSL.() -> Unit) = Subscript(Block(Block.BlockDSL(flavor).apply(block).build()))

        override val id: String = "subscript"
        override val flavor: Flavor = BlogFlavor

        override fun convertToFlavor(element: Subscript, flavor: Flavor): MarkdownElement {
            return Subscript(element.element)
        }

        override fun convertFromElement(element: MarkdownElement): Subscript? = null
    }
}