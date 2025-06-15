package io.github.devngho.geulgyeol.site

import com.vladsch.flexmark.ext.escaped.character.EscapedCharacterExtension
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import io.github.devngho.geulgyeol.Crawler
import io.github.devngho.geulgyeol.ListRelatedPageTask
import io.github.devngho.geulgyeol.data.CCL
import io.github.devngho.geulgyeol.data.CrawledData
import io.github.devngho.geulgyeol.data.Post
import io.github.devngho.geulgyeol.store.Store
import io.github.devngho.geulgyeol.site.SiteUtil.parseCCL
import io.github.devngho.geulgyeol.site.blog.BlogComponent
import io.github.devngho.geulgyeol.site.blog.Tistory.parseArticle
import io.github.devngho.geulgyeol.site.blog.flavor.BlogFlavor
import io.github.devngho.geulgyeol.site.blog.flavor.BlogPlainFlavor
import io.github.devngho.geulgyeol.util.MarkdownStyler
import io.github.devngho.geulgyeol.util.UrlSerializer
import io.github.devngho.kmarkdown.builder.MarkdownDSL
import io.ktor.client.statement.*
import io.ktor.http.*
import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import it.skrape.selects.ElementNotFoundException
import it.skrape.selects.eachText
import it.skrape.selects.html5.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.serializersModuleOf
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


object TistoryBlogParser: SiteParser<Post> {
    private val json = Json {
        ignoreUnknownKeys = true

        serializersModule = serializersModule + serializersModuleOf(UrlSerializer)
    }
    private val converter = FlexmarkHtmlConverter.builder(MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(EscapedCharacterExtension.create()))
        set(OUTPUT_ATTRIBUTES_ID, false)
    }).build()
    private val store: Store by inject()

    override fun isTarget(url: Url): Boolean = url.host.endsWith("tistory.com")

    private data class DocMetadata(val title: String, val author: String, val publishedAt: Long, val cclConditions: List<CCL.CCLConditions>, val postLinks: List<Url>)
    private fun Doc.getMetadata(): DocMetadata {
        var title = ""
        var author = ""
        var publishedAt: Long = 0
        val postLinks = mutableListOf<Url>()

        meta {
            withAttribute = "property" to "og:title"

            findFirst {
                title = attribute("content")
            }
        }

        meta {
            withAttribute = "name" to "by"

            findFirst {
                author = attribute("content")
            }
        }


        meta {
            withAttribute = "property" to "article:published_time"

            findFirst {
                publishedAt = LocalDateTime.parse(attribute("content"), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond(ZoneOffset.UTC)
            }
        }

        return DocMetadata(title, author, publishedAt, findFirst(".container_postbtn").parseCCL(), postLinks)
    }

    @Serializable
    data class TistoryCommentWriter(val homepage: String)

    @Serializable
    data class TistoryComment(val writer: TistoryCommentWriter)

    @Serializable
    data class TistoryCommentData(val items: List<TistoryComment>, val isLast: Boolean, val nextId: Long)

    @Serializable
    data class TistoryCommentResponse(val data: TistoryCommentData)


    suspend fun loadPages(crawler: Crawler, userId: String, page: Int = 1): Pair<List<Url>, List<ListRelatedPageTask>> {
        val cacheKey = "https://$userId.tistory.com/__pages_cache_$page"
        val prevCacheKey = "https://$userId.tistory.com/__pages_cache_${page-1}"
        val links = mutableListOf<Url>()
        if (page != 1) {
            if (!store.linksExists(prevCacheKey)) {
                return emptyList<Url>() to listOf(ListRelatedPageTask("$userId$${page-1}", "list_user_pages", "tistory"))
            }

            val prevLinks = store.getLinks(prevCacheKey).getOrNull().orEmpty()

            if (prevLinks.isEmpty()) {
                return emptyList<Url>() to emptyList()
            }

            links.addAll(prevLinks)
        }
        val pageLinks = mutableListOf<Url>()
        var isLastPagePassed = true

        if (!store.linksExists(cacheKey)) {
            try {
                crawler.get(Url("https://$userId.tistory.com/?page=$page"))
                    .map {
                        htmlDocument(it.bodyAsText()) {
                            try {
                                div(".post-item") {
                                    findAll {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }

                            try {
                                div(".item") {
                                    findAll {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }

                            try {
                                findFirst("div.cover-list") {
                                    findAll("li") {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }

                            try {
                                div(".article-content") {
                                    findAll {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }

                            try {
                                li(".item_category") {
                                    findAll {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }

                            try {
                                div(".list_content") {
                                    findAll {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }

                            try {
                                div(".list.content__index") {
                                    findAll {
                                        forEach {
                                            it.a {
                                                findFirst {
                                                    pageLinks.add(
                                                        convertURLToCommon(
                                                            Url(
                                                                "https://$userId.tistory.com" + attribute(
                                                                    "href"
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ElementNotFoundException) { }
                        }
                    }

                if (!(pageLinks.isEmpty() || links.lastOrNull() == pageLinks.lastOrNull())) {
                    isLastPagePassed = false
                } else {
                    // last page and these are the duplicates
                    pageLinks.clear()
                }
            } catch (_: Exception) { }

            store.putLinks(cacheKey, pageLinks)
        } else {
            return emptyList<Url>() to emptyList()
        }

        return pageLinks.distinct().toList() to if (isLastPagePassed) emptyList() else listOf(ListRelatedPageTask("$userId$${page+1}", "list_user_pages", "tistory"))
    }

    suspend fun loadComments(crawler: Crawler, userId: String, postId: String, startId: Long? = null): Pair<List<Url>, List<ListRelatedPageTask>> = runCatching {
        val users = mutableSetOf<String>()
        var nextStartId: Long? = null

        if (startId == null) {
            // create cache key
            val cacheKey = "https://$userId.tistory.com/$postId/__sympathy_users_cache" // compatibility
            if (store.linksExists(cacheKey)) return emptyList<Url>() to emptyList()
            else {
                store.putLinks(cacheKey, emptyList())
            }
        } else {
            nextStartId = startId
        }

        val response = crawler.get(Url("https://$userId.tistory.com/m/api/$postId/comment?startId=${nextStartId ?: ""}")).getOrNull() ?: return emptyList<Url>() to emptyList()

        val commentResponse = json.decodeFromString<TistoryCommentResponse>(response.bodyAsText())

        users.addAll(commentResponse.data.items.map { it.writer.homepage }.distinct().filter {
            it.contains("tistory.com")
        }.map {
            val url = Url(it)
            val userId = url.host.split(".")[0]

            userId
        })

        if (commentResponse.data.isLast) {
            return emptyList<Url>() to users.map { ListRelatedPageTask(it, "list_user_pages", "tistory") }
        }

        nextStartId = commentResponse.data.nextId

        return emptyList<Url>() to users.map { ListRelatedPageTask(it, "list_user_pages", "tistory") } + listOf(ListRelatedPageTask("$userId$$postId$${nextStartId}", "list_comments", "tistory"))
    }.onFailure {
        if (it is CancellationException) throw it
    }.getOrDefault(emptyList<Url>() to emptyList())

    override suspend fun parse(crawler: Crawler, url: Url): Result<Triple<CrawledData<Post>, List<Url>, List<ListRelatedPageTask>>> = runCatching { crawler.get(url).getOrElse { throw it }.bodyAsText() }.onFailure { if (it is CancellationException) throw it else return Result.failure<Triple<CrawledData<Post>, List<Url>, List<ListRelatedPageTask>>>(it) }.getOrThrow().let { body -> kotlin.runCatching {
        var metadata: DocMetadata? = null
        var postBody: BlogComponent.Article? = null

        htmlDocument(body) {
            runCatching {
                findFirst("div.contents_style")
            }.recoverCatching {
                findFirst("div.tt_article_useless_p_margin")
            }.recoverCatching {
                findFirst("div.area_view").children.filterNot { it.className.contains("revenue") }.first()
            }.recoverCatching {
                findFirst("div.entry-content").children.filterNot { it.className.contains("revenue") }.first()
            }.recoverCatching {
                findFirst("div.article-view").children.filterNot { it.className.contains("revenue") }.first()
            }.getOrThrow().run {
                postBody = parseArticle()
            }

            metadata = getMetadata()
        }

        Triple(CrawledData(
            url,
            Post(
                url.toString(),
                postBody!!.toText(),
                BlogFlavor.build(MarkdownDSL(BlogFlavor).apply { postBody.apply { markdown() } }.elements.let {
                    MarkdownStyler.merge(it)
                }),
                BlogPlainFlavor.build(MarkdownDSL(BlogPlainFlavor).apply { postBody.apply { markdown() } }.elements.let {
                    MarkdownStyler.merge(it)
                }),
                System.currentTimeMillis() / 1000,
                json.encodeToString(postBody),
                metadata!!.title,
                metadata.author,
                metadata.publishedAt,
                CCL(metadata.cclConditions, url.toString())
            )
        ), metadata.postLinks + postBody.links.map { Url(it) }.distinct(), mutableListOf<ListRelatedPageTask>())
    }.recoverCatching {
        if (it is CancellationException) throw it

        // try normal html parsing
        var metadata: DocMetadata? = null
        var postBody: String = "" // html
        var postText: String = "" // plain text

        htmlDocument(body) {
            metadata = getMetadata()

            div(".tt_article_useless_p_margin") {
                findFirst {
                    postBody = html
                }

                postText = p { findAll { eachText } }.joinToString("\n")
            }
        }

        // remove img tags
        postBody = postBody.replace(Regex("""<img[^>]*>"""), "")

        var markdown = converter.convert(postBody)

        // remove <br> tags
        markdown = markdown.replace(Regex("""<br />"""), "")

        Triple(CrawledData(
            url,
            Post(
                url.toString(),
                postText,
                markdown,
                markdown.replace(Regex("""!\[.*]\(.*\)"""), ""),
                System.currentTimeMillis() / 1000,
                postBody,
                metadata!!.title,
                metadata.author,
                metadata.publishedAt,
                CCL(metadata.cclConditions, url.toString())
            ),
        ), metadata.postLinks, mutableListOf())
    }.onFailure {
        if (it is CancellationException) throw it
    }.also { crawledData ->
        if (crawledData.isSuccess) {
            val userId = url.host.split(".")[0]
            val postId = url.segments.joinToString("/")

            crawledData.getOrThrow().third.let {
                it.add(ListRelatedPageTask("$userId$$postId", "list_comments", "tistory"))
                it.add(ListRelatedPageTask(userId, "list_user_pages", "tistory"))
            }
        }
    } }

    override suspend fun listRelatedPages(crawler: Crawler, target: String, type: String): Pair<List<Url>, List<ListRelatedPageTask>> {
        when (type) {
            "list_comments" -> {
                val split = target.split("$")
                if (split.size == 3) {
                    val (userId, postId, startId) = split

                    return loadComments(crawler, userId, postId, startId.toLong())
                } else {
                    val (userId, postId) = split

                    return loadComments(crawler, userId, postId)
                }
            }
            "list_user_pages" -> {
                if (target.contains("$")) {
                    val (userId, page) = target.split("$")

                    return loadPages(crawler, userId, page.toInt())
                } else {
                    return loadPages(crawler, target)
                }
            }

            else -> return emptyList<Url>() to emptyList()
        }
    }

    override fun convertURLToCrawl(url: Url): Url = Url(url.toString().let {
        if (it.endsWith("/")) it else "$it/"
    }.replace("tistory.com/m/", "tistory.com/"))

    override fun convertURLToCommon(url: Url): Url = url
}