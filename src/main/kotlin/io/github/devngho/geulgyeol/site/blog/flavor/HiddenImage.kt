package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.builder.MarkdownDSLMarker
import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor

@MarkdownDSLMarker
data object HiddenImage: MarkdownElement, MarkdownElementDescriptor<HiddenImage> {
    override val descriptor: MarkdownElementDescriptor<out MarkdownElement> = HiddenImage

    override fun encode(): String = ""

    override val id: String = "image"
    override val flavor: Flavor = BlogFlavor

    override fun convertToFlavor(element: HiddenImage, flavor: Flavor): MarkdownElement = HiddenImage

    override fun convertFromElement(element: MarkdownElement): HiddenImage? = when (element) {
        is Image -> HiddenImage
        else -> null
    }
}