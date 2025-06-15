package io.github.devngho.geulgyeol.site.blog.flavor

import io.github.devngho.kmarkdown.flavor.Flavor
import io.github.devngho.kmarkdown.flavor.Flavor.Companion.convertTo
import io.github.devngho.kmarkdown.flavor.MarkdownElement
import io.github.devngho.kmarkdown.flavor.MarkdownElementDescriptor
import io.github.devngho.kmarkdown.flavor.common.CommonFlavor
import io.github.devngho.kmarkdown.flavor.gfm.GFMFlavor

object BlogPlainFlavor: Flavor by CommonFlavor {
    override val elements: Map<String, MarkdownElementDescriptor<*>> = BlogFlavor.elements + listOf(
        HiddenImage
    ).associateBy { it.id }

    override fun build(elements: List<MarkdownElement>): String = elements.joinToString(separator = "\n\n") { it.convertTo(this).encode() }.replace(Regex("\n{4,}"), "\n\n")
}