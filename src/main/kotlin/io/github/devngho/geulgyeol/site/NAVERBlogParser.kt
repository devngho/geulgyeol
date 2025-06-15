package io.github.devngho.geulgyeol.site

import com.vladsch.flexmark.ext.escaped.character.EscapedCharacterExtension
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
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
import io.github.devngho.geulgyeol.site.blog.NAVERBlog
import io.github.devngho.geulgyeol.site.blog.NAVERBlog.parseArticle
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.serializersModuleOf
import org.jsoup.nodes.Node
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


object NAVERBlogParser: SiteParser<Post> {
    private val json = Json {
        ignoreUnknownKeys = true

        serializersModule = serializersModule + serializersModuleOf(UrlSerializer)
    }
    private val publishedDateFormatter = DateTimeFormatter.ofPattern("yyyy. M. d. H:mm")
    private val converter = FlexmarkHtmlConverter.builder(MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(EscapedCharacterExtension.create()))
    }).build()
    private val store: Store by inject()

    override fun isTarget(url: Url): Boolean = (url.host == "blog.naver.com" || url.host == "m.blog.naver.com") && (url.segments.getOrNull(1)?.let { it == "moment" || it == "clip" } != true)

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
            withAttribute = "property" to "naverblog:nickname"

            findFirst {
                author = attribute("content")
            }
        }
        val publishedText = try { span(".se_publishDate") {
            findFirst {
                text
            }
        } } catch (_: ElementNotFoundException) {
            p("._postAddDate") {
                findFirst {
                    text
                }
            }
        }

        publishedAt = if (publishedText == "방금 전") {
            System.currentTimeMillis() / 1000
        } else if (publishedText.matches(Regex("""\d+분 전"""))) {
            System.currentTimeMillis() / 1000 - publishedText.replace("분 전", "").toLong() * 60 * 1000
        } else if (publishedText.matches(Regex( /*  3시간 전 */ """\d+시간 전"""))) {
            System.currentTimeMillis() / 1000 - publishedText.replace("시간 전", "").toLong() * 60 * 60 * 1000
        } else {
            kotlin.runCatching {
                publishedDateFormatter.parse(publishedText).let {
                    LocalDateTime.from(it).toEpochSecond(ZoneOffset.ofHours(9))
                }
            }.getOrDefault(0L)
        }

        return DocMetadata(title, author, publishedAt, findFirst("#post_footer_contents").parseCCL(), postLinks)
    }

    suspend fun loadPages(crawler: Crawler, userId: String, page: Int = 1): Pair<List<Url>, List<ListRelatedPageTask>> {
        val cacheKey = "https://blog.naver.com/$userId/__pages_cache_${page}"
        val prevKey = "https://blog.naver.com/$userId/__pages_cache_${page - 1}"
        val links = mutableListOf<Url>()
        val pageLinks = mutableListOf<Url>()
        if (page != 1) {
            if (!store.linksExists(prevKey)) return emptyList<Url>() to listOf(ListRelatedPageTask("$userId$${page-1}", "list_user_pages", "naver")) // backward

            val prevLinks = store.getLinks(prevKey).getOrNull() ?: return emptyList<Url>() to listOf(ListRelatedPageTask("$userId$${page-1}", "list_user_pages", "naver")) // backward
            links.addAll(prevLinks)
        }

        var isLastPagePassed = true // 이전 페이지가 마지막 페이지인지

        if (!store.linksExists(cacheKey)) {
            var backoff = 1L
            var retry = 0
            val maxRetry = 3

            while (true) {
                try {
                    val pageLinkResponse =
                        crawler.get(Url("https://blog.naver.com/PostTitleListAsync.naver?blogId=${userId}&currentPage=${page}&countPerPage=30"))
                            .getOrNull() ?: break

                    val e = json.parseToJsonElement(pageLinkResponse.bodyAsText().replace("\\'", "'"))
                    if (e.jsonObject["resultCode"]?.jsonPrimitive?.content == "E") {
                        if (retry >= maxRetry) break

                        retry++
                        // rate limited
                        delay(2000 * backoff)
                        backoff *= 2
                        continue
                    }

                    json.decodeFromString<NAVERBlog.PostTitleList>(
                        pageLinkResponse.bodyAsText().replace("\\'", "'")
                    ).postList.map { convertURLToCommon(Url("https://blog.naver.com/PostView.nhn?blogId=${userId}&logNo=${it.logNo}")) }.let {
                        pageLinks.addAll(it)
                    }

                    if (pageLinks.isEmpty() || pageLinks.last() == links.lastOrNull()) {
                        pageLinks.clear() // these pages are duplicated
                        break
                    }

                    isLastPagePassed = false
                    break
                } catch (_: Exception) {
                    break
                }
            }

            store.putLinks(cacheKey, pageLinks)
        } else {
            return emptyList<Url>() to emptyList()
        }

        return pageLinks.distinct().toList() to if (isLastPagePassed) emptyList() else listOf(ListRelatedPageTask("$userId$${page+1}", "list_user_pages", "naver"))
    }

    @Serializable data class NAVERBlogSympathyUser(val domainIdOrBlogId: String?)
    @Serializable data class NAVERBlogSympathyUsersResult(val nextTimestamp: Long, val users: List<NAVERBlogSympathyUser>)
    @Serializable data class NAVERBlogSympathyUsersResponse(val result: NAVERBlogSympathyUsersResult)

    suspend fun getSympathyUsers(crawler: Crawler, userId: String, postId: String, timestamp: Long? = null): List<ListRelatedPageTask> = runCatching {
        val users = mutableSetOf<String>()
        var nextTimestamp: Long

        if (timestamp == null) {
            nextTimestamp = System.currentTimeMillis()
            // create cache key
            val cacheKey = "https://blog.naver.com/$userId/$postId/__sympathy_users_cache"
            if (store.linksExists(cacheKey)) return emptyList()
            else {
                store.putLinks(cacheKey, emptyList())
            }
        } else {
            nextTimestamp = timestamp
        }

        val response = crawler.get(Url("https://blog.naver.com/api/blogs/$userId/posts/$postId/sympathy-users?itemCount=100&timeStamp=$nextTimestamp"), mapOf(
            "Referer" to "https://blog.naver.com/SympathyHistoryList.naver?blogId=$userId&logNo=$postId",
//                "Content-Type" to "application/x-www-form-urlencoded; charset=utf-8",
        )).getOrNull() ?: return emptyList()

        val sympathyUsers = json.decodeFromString<NAVERBlogSympathyUsersResponse>(response.bodyAsText())

        users.addAll(sympathyUsers.result.users.mapNotNull { it.domainIdOrBlogId })

        if (sympathyUsers.result.nextTimestamp == -1L) {
            return users.map { ListRelatedPageTask(it, "list_user_pages", "naver") }
        }

        nextTimestamp = sympathyUsers.result.nextTimestamp

        return listOf(ListRelatedPageTask("$userId/$postId$${nextTimestamp}", "list_sympathy_users", "naver")) + users.map { ListRelatedPageTask(it, "list_user_pages", "naver") }
    }.onFailure {
        if (it is CancellationException) throw it
    }.getOrDefault(emptyList())

    override suspend fun parse(crawler: Crawler, url: Url): Result<Triple<CrawledData<Post>?, List<Url>, List<ListRelatedPageTask>>> {
        // if link is user page, just add related page task
        if (!url.segments.getOrNull(0).let { it == "PostView.nhn" || it == "PostView.naver" }) {
            return runCatching {
                val userId = url.segments[0]
                Triple(null, emptyList(), listOf(ListRelatedPageTask(userId, "list_user_pages", "naver")))
            }
        }

        return runCatching { crawler.get(url).getOrThrow().bodyAsText() }.onFailure { if (it is CancellationException) throw it else return Result.failure<Triple<CrawledData<Post>, List<Url>, MutableList<ListRelatedPageTask>>>(it) }.getOrThrow().let { body -> kotlin.runCatching {
            var metadata: DocMetadata? = null
            var postBody: BlogComponent.Article? = null

            htmlDocument(body) {
                runCatching {
                    findFirst("div.se-main-container")
                }.recoverCatching {
                    findFirst("div.sect_dsc")
                }.getOrThrow().apply {
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
                ),
            ), metadata.postLinks + postBody.links.map { Url(it) }.distinct(), mutableListOf<ListRelatedPageTask>())
        }.recoverCatching {
            if (it is CancellationException) throw it

            println("Fallback: $url as $it")
            // try normal html parsing
            var metadata: DocMetadata? = null
            var postBody = "" // html
            var postText = "" // plain text

            htmlDocument(body) {
                metadata = getMetadata()

                runCatching {
                    findFirst("div#postViewArea").let {
                        it.runCatching { findFirst(".post-view") }.getOrDefault(it)
                    }
                }.recoverCatching {
                    findFirst("div.sect_dsc")
                }.getOrThrow().apply {
                    postText = runCatching { p { findAll { eachText } }.joinToString("\n") }.recoverCatching { text }.getOrDefault("")

                    // walk the elements, if you found a table with single tbody, and single td each row, flatten it
                    fun processNodes(node: Node): String /* html */ {
                        val sb = StringBuilder()

                        node.childNodes().forEach {
                            when(it.nodeName()) {
                                "table" -> if (it.childNode(0).nodeName() == "tbody" && it.childNodeSize() == 1) {
                                    // check every row
                                    val everyRowsHaveSingleTd = it.childNode(0).childNodes().all { row ->
                                        row.nodeName() == "tr" && row.childNodes().size == 1 && row.childNode(0)
                                            .nodeName() == "td"
                                    }

                                    if (everyRowsHaveSingleTd) {
                                        it.childNode(0).childNodes().forEach { row ->
                                            sb.append(processNodes(row.childNode(0)))
                                        }
                                    } else {
                                        sb.append(processNodes(it).let { "<table>$it</table>" })
                                    }
                                }

                                "#text" -> sb.append(it.outerHtml())
                                else -> sb.append("<${it.nodeName()}>${processNodes(it)}</${it.nodeName()}>")
                            }
                        }

                        return sb.toString()
                    }

                    postBody = processNodes(element)
                }
            }

            var markdown = converter.convert(postBody)
            println("fallback converted: $url")

            // remove <br> tags and replace 3+ \n to 2 \n
            markdown = markdown.replace("<br />", "\n").replace(Regex("""\n{3,}"""), "\n\n")
            val markdownText = markdown.replace(Regex("""!\[.*]\(.*\)"""), "")

            println("fallback postprocessing done: $url")

            Triple(CrawledData(
                url,
                Post(
                    url.toString(),
                    postText,
                    markdown,
                    markdownText,
                    System.currentTimeMillis() / 1000,
                    postBody,
                    metadata!!.title,
                    metadata.author,
                    metadata.publishedAt,
                    CCL(metadata.cclConditions, url.toString())
                ),
            ), metadata.postLinks, mutableListOf<ListRelatedPageTask>())
        }.also {
            if (it.isSuccess) {
                val userId = url.parameters["blogId"] ?: throw IllegalArgumentException("Invalid URL")

                it.getOrThrow().third.let {
                    it.add(ListRelatedPageTask(
                        userId,
                        "list_user_pages",
                        "naver"
                    ))
                    it.add(ListRelatedPageTask(
                        "$userId/${url.parameters["logNo"]}",
                        "list_sympathy_users",
                        "naver"
                    ))
                }
            }
        } }
    }

    override suspend fun listRelatedPages(crawler: Crawler, target: String, type: String): Pair<List<Url>, List<ListRelatedPageTask>> {
        when (type) {
            "list_user_pages" -> {
                if (target.contains("$")) {
                    val (userId, page) = target.split("$")
                    return loadPages(crawler, userId, page.toInt())
                } else {
                    return loadPages(crawler, target)
                }
            }
            "list_sympathy_users" -> {
                val (userId, postId) = target.split("/")
                if (postId.contains("$")) {
                    val (postId, timestamp) = postId.split("$")
                    return emptyList<Url>() to getSympathyUsers(crawler, userId, postId, timestamp.toLong())
                } else {
                    return emptyList<Url>() to getSympathyUsers(crawler, userId, postId)
                }
            }

            else -> return emptyList<Url>() to emptyList()
        }
    }

    override fun convertURLToCrawl(url: Url): Url = runCatching {
        // if mobile URL, convert to desktop URL
        if (url.host == "m.blog.naver.com") return@runCatching convertURLToCrawl(Url(url.toString().replace("m.blog.naver.com", "blog.naver.com")))

        if (url.encodedPath.startsWith("/PostView.nhn")) return url
        if (url.encodedPath.startsWith("/PostView.naver")) return url

        if (url.parameters.contains("Redirect")) {
            val userId = url.segments[0]
            val postId = url.parameters["logNo"]!!

            return Url("https://blog.naver.com/PostView.nhn?blogId=$userId&logNo=$postId")
        }

        val userId = url.segments[0]
        val postId = url.segments[1]

        if (userId.isEmpty() || postId.isEmpty()) throw IllegalArgumentException("Invalid URL")

        return Url("https://blog.naver.com/PostView.nhn?blogId=$userId&logNo=$postId")
    }.onFailure {
        if (it is CancellationException) throw it
    }.getOrDefault(url)

    override fun convertURLToCommon(url: Url): Url {
        if (!url.encodedPath.startsWith("/PostView.nhn") || url.encodedPath.startsWith("/PostView.naver")) return url
        if (!url.parameters.contains("logNo")) return Url("https://blog.naver.com/${url.parameters["blogId"]}")

        val userId = url.parameters["blogId"]!!
        val postId = url.parameters["logNo"]!!

        return Url("https://blog.naver.com/$userId/$postId")
    }
}