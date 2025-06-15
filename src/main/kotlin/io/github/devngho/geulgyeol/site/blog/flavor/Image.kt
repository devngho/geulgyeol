package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.github.devngho.kmarkdown.builder.MarkdownDSLMarker
import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor
import io.github.devngho.kmarkdown.flavor.common.Block
import io.github.devngho.kmarkdown.flavor.common.Raw.Companion.raw

@MarkdownDSLMarker
data class Image(val url: String, val altText: String?, val caption: String?): MarkdownElement {
    override val descriptor: MarkdownElementDescriptor<out MarkdownElement> = Image

    override fun encode(): String = "![${caption ?: altText ?: "사진"}]($url)"

    companion object: MarkdownElementDescriptor<Image> {
        fun MarkdownDSL.img(url: String, altText: String? = null, caption: String? = null) = Image(url, altText, caption)
        fun Block.BlockDSL.img(url: String, altText: String? = null, caption: String? = null) = Image(url, altText, caption)

        override val id: String = "image"
        override val flavor: Flavor = BlogFlavor

        override fun convertToFlavor(element: Image, flavor: Flavor): MarkdownElement {
            return Image(element.url, element.altText, element.caption)
        }

        override fun convertFromElement(element: MarkdownElement): Image? = null
    }
}