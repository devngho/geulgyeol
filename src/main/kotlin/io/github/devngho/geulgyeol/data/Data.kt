package io.github.devngho.geulgyeol.data

import kotlinx.serialization.Serializable

@Serializable
sealed interface Data {
    /**
     * 자료 URL
     */
    val url: String

    /**
     * 자료 본문
     */
    val text: String

    /**
     * 자료 본문 Markdown
     */
    val markdown: String

    /**
     * 자료 본문 Markdown, 이미지 등 제외
     */
    val markdownText: String

    /**
     * 자료 접근 일시
     */
    val accessedAt: Long
}