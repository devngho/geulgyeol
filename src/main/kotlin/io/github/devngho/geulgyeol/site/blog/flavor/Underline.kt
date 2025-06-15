package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.github.devngho.kmarkdown.builder.MarkdownDSLMarker
import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor
import io.github.devngho.kmarkdown.flavor.common.Block

@MarkdownDSLMarker
data class Underline(var element: Block): MarkdownElement {
    override val descriptor: MarkdownElementDescriptor<out MarkdownElement> = Underline

    override fun encode(): String = "++${element.encode()}++"

    companion object: MarkdownElementDescriptor<Underline> {
        fun MarkdownDSL.sub(block: Block.BlockDSL.() -> Unit) = Underline(Block(Block.BlockDSL(this.flavor).apply(block).build()))
        fun Block.BlockDSL.sub(block: Block.BlockDSL.() -> Unit) = Underline(Block(Block.BlockDSL(flavor).apply(block).build()))

        override val id: String = "underline"
        override val flavor: Flavor = BlogFlavor

        override fun convertToFlavor(element: Underline, flavor: Flavor): MarkdownElement {
            return Underline(element.element)
        }

        override fun convertFromElement(element: MarkdownElement): Underline? = null
    }
}