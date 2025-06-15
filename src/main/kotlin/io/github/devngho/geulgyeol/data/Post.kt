package io.github.devngho.geulgyeol.data

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    override val url: String,
    override val text: String,
    override val markdown: String,
    override val markdownText: String,
    override val accessedAt: Long,
    val raw: String,
    val title: String,
    val author: String,
    val writtenAt: Long,
    val ccl: CCL
): Data