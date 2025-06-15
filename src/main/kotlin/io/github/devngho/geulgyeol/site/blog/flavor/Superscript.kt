package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.github.devngho.kmarkdown.builder.MarkdownDSLMarker
import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor
import io.github.devngho.kmarkdown.flavor.common.Block

@MarkdownDSLMarker
data class Superscript(var element: Block): MarkdownElement {
    override val descriptor: MarkdownElementDescriptor<out MarkdownElement> = Superscript

    override fun encode(): String = "^${element.encode()}^"

    companion object: MarkdownElementDescriptor<Superscript> {
        fun MarkdownDSL.sup(block: Block.BlockDSL.() -> Unit) = Superscript(Block(Block.BlockDSL(this.flavor).apply(block).build()))
        fun Block.BlockDSL.sup(block: Block.BlockDSL.() -> Unit) = Superscript(Block(Block.BlockDSL(flavor).apply(block).build()))

        override val id: String = "superscript"
        override val flavor: Flavor = BlogFlavor

        override fun convertToFlavor(element: Superscript, flavor: Flavor): MarkdownElement {
            return Superscript(element.element)
        }

        override fun convertFromElement(element: MarkdownElement): Superscript? = null
    }
}