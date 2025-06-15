package io.github.devngho.geulgyeol.site

import io.github.devngho.geulgyeol.data.CCL
import it.skrape.selects.Doc
import it.skrape.selects.DocElement
import it.skrape.selects.ElementNotFoundException
import it.skrape.selects.html5.span

object SiteUtil {
    fun DocElement.parseCCL(): MutableList<CCL.CCLConditions> {
        val cclConditions = mutableListOf<CCL.CCLConditions>()

        try {
            span(".ico_ccl1") {
                findFirst {
                    cclConditions.add(CCL.CCLConditions.BY)
                }
            }
        } catch (_: ElementNotFoundException) {}

        try {
            span(".ico_ccl2") {
                findFirst {
                    cclConditions.add(CCL.CCLConditions.NC)
                }
            }
        } catch (_: ElementNotFoundException) {}

        try {
            span(".ico_ccl3") {
                findFirst {
                    cclConditions.add(CCL.CCLConditions.ND)
                }
            }
        } catch (_: ElementNotFoundException) {}

        try {
            span(".ico_ccl4") {
                findFirst {
                    cclConditions.add(CCL.CCLConditions.SA)
                }
            }
        } catch (_: ElementNotFoundException) {}

        return cclConditions
    }
}