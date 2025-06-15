package io.github.devngho.geulgyeol.site

import io.github.devngho.geulgyeol.Crawler
import io.github.devngho.geulgyeol.ListRelatedPageTask
import io.github.devngho.geulgyeol.data.CrawledData
import io.github.devngho.geulgyeol.data.Data
import io.github.devngho.geulgyeol.data.Post
import io.ktor.http.*
import org.koin.core.component.KoinComponent

sealed interface SiteParser<T: Data>: KoinComponent {
    fun isTarget(url: Url): Boolean

    fun convertURLToCrawl(url: Url): Url
    fun convertURLToCommon(url: Url): Url

    /**
     * @return Triple of CrawledData, List of direct links, List of the target to list related pages
     */
    suspend fun parse(crawler: Crawler, url: Url): Result<Triple<CrawledData<Post>?, List<Url>, List<ListRelatedPageTask>>>
    suspend fun listRelatedPages(crawler: Crawler, target: String, type: String): Pair<List<Url>, List<ListRelatedPageTask>>

    companion object {
        val registry = mutableMapOf<String, SiteParser<*>>(
            "naver" to NAVERBlogParser,
            "tistory" to TistoryBlogParser,
        )

        inline fun <reified T: Data> register(name: String, parser: SiteParser<T>) {
            registry.put(name, parser)
        }

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T: Data> find(url: Url): SiteParser<T>? {
            return registry.values.firstOrNull { it.isTarget(url) } as? SiteParser<T>
        }
    }
}