package io.github.devngho.geulgyeol

import kotlinx.serialization.Serializable

@Serializable
data class CrawlTask(
    val target: String
)

@Serializable
data class ListRelatedPageTask(
    val target: String,
    /** derived from site parser */
    val type: String,
    val site: String
)