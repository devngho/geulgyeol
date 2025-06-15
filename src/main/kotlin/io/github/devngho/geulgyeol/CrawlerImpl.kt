package io.github.devngho.geulgyeol

import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.stopAtAttempts
import com.github.michaelbull.retry.retry
import crawlercommons.robots.SimpleRobotRules
import crawlercommons.robots.SimpleRobotRulesParser
import io.github.devngho.geulgyeol.data.Data
import io.github.devngho.geulgyeol.store.Store
import io.github.devngho.geulgyeol.metrics.Metrics.Companion.measured
import io.github.devngho.geulgyeol.site.SiteParser
import io.github.devngho.geulgyeol.task.TaskInserter
import io.github.reactivecircus.cache4k.Cache
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.inject
import kotlin.math.max
import kotlin.time.Duration.Companion.days
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class CrawlerImpl(): Crawler {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0; compatible; geulgyeol-crawler/1.0; +https://github.com/devngho/geulgyeol-crawler) Gecko/20100101 Firefox/135.0"
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }
    }
    private val robotsRuleMutex =
        mutableMapOf<String, Mutex>()
    private val robotsRule =
        Cache.Builder<String, SimpleRobotRules>()
            .expireAfterWrite(1.days)
            .build()
    private val delay =
        mutableMapOf<String, Mutex>()
    private val delayTimeMark =
        mutableMapOf<String, TimeMark>()
    private val defaultDelay = 100L
    /** The number of how many processes can be got at the same time in a host */
    private val parallelHost = 2
    private val scope = CoroutineScope(Dispatchers.Default)

    private val taskInserter: TaskInserter by inject()
    private val store: Store by inject()

    private suspend fun prepareRequest(url: Url) {
        val host = url.host.let {
            // if the host is tistory.com, we should use the root domain
            if (it.endsWith("tistory.com")) "tistory.com" else it
        }

        if (delay[host] == null) {
            delay[host] = Mutex()
            delayTimeMark[host] = TimeSource.Monotonic.markNow()
        }

        if (robotsRule.get(url.host) == null) {
            if (robotsRuleMutex[url.host] == null) {
                robotsRuleMutex[url.host] = Mutex()
            }

            robotsRuleMutex[url.host]!!.withLock {
                if (robotsRule.get(url.host) != null) return@withLock

                val resp = client.get(Url("${url.protocol.name}://${url.host}/robots.txt"))
                val rule = SimpleRobotRulesParser().parseContent(
                    url.toString(),
                    resp.readRawBytes(),
                    resp.contentType()?.contentType ?: "text/plain",
                    listOf("geulgyeol")
                )

                robotsRule.put(url.host, rule)
            }
        }
    }

    private val requestChannelByHost = mutableMapOf<String, Channel<Triple<Url, Map<String, String>?, suspend (res: Result<HttpResponse>) -> Unit>>>()
    private val scopeByHost = mutableMapOf<String, CoroutineScope>()
    private val requestChannelMutex = Mutex()
    private var queuedCount = 0
    private var queuedCountMutex = Mutex()

    suspend fun setupScope(host: String) = requestChannelMutex.withLock {
        if (requestChannelByHost[host] == null) {
            requestChannelByHost[host] = Channel(Channel.UNLIMITED)
        }

        if (scopeByHost[host] == null) {
            scopeByHost[host] = CoroutineScope(Dispatchers.Default)
            repeat(parallelHost) {
                scopeByHost[host]!!.launch {
                    for ((url, headers, handler) in requestChannelByHost[host]!!) {
                        handler(processRequest(url, headers))
                        queuedCountMutex.withLock {
                            queuedCount--
                        }
                    }
                }
            }
        }
    }

    init {
        scope.launch {
            while (true) {
                delay(1000)
                println("Queued count: $queuedCount")
            }
        }
    }

    private suspend fun processRequest(url: Url, headers: Map<String, String>?): Result<HttpResponse> = measured("crawler.processRequest", mapOf("url" to url.toString())) { runCatching {
        retry(constantDelay<Throwable>(200L) + stopAtAttempts<Throwable>(3)) {
            println("Requesting $url")
            prepareRequest(url)
            val host = url.host.let {
                // if the host is tistory.com, we should use the root domain
                if (it.endsWith("tistory.com")) "tistory.com" else it
            }

            if (!robotsRule.get(url.host)!!.isAllowed(url.toString())) {
                appendMetadata("result", "robots_disallowed")
                return@measured Result.failure(Exception("robots.txt disallowed this URL"))
            }

            val shouldDelay = max(robotsRule.get(url.host)!!.crawlDelay * 1000, defaultDelay)

            delay[host]!!.withLock {
                val diff = delayTimeMark[host]!!.elapsedNow().inWholeMilliseconds

                if (diff < shouldDelay) { // too fast
                    delayTimeMark[host] = TimeSource.Monotonic.markNow()
                    println("Delaying $host for ${shouldDelay - diff}ms")
                    delay(shouldDelay - diff)
                } else {
                    delayTimeMark[host] = TimeSource.Monotonic.markNow()
                }
            }

            client.get(url) {
                headers {
                    append("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> append(k, v) }
                }
            }.also {
                if (it.status.value >= 400) {
                    appendMetadata("result", "http_error")
                    appendMetadata("http_status", it.status.value.toString())
                    println("HTTP error: ${it.status.value} for $url")
                } else {
                    appendMetadata("result", "success")
                }
            }
        }
    } }

    // Modify the get function to send requests to the channel
    override suspend fun get(url: Url, headers: Map<String, String>?): Result<HttpResponse> = measured("crawler.get", mapOf("url" to url.toString())) {
        coroutineScope {
            queuedCountMutex.withLock {
                queuedCount++
            }

            val response = CompletableDeferred<Result<HttpResponse>>()
            val host = url.host.let {
                // if the host is tistory.com, we should use the root domain
                if (it.endsWith("tistory.com")) "tistory.com" else it
            }

            setupScope(host)

            requestChannelByHost[host]!!.send(Triple(url, headers) {
                response.complete(it)
            })

            response.await()
        }
    }

    override suspend fun processCrawlTask(data: CrawlTask) {
        measured("crawler.processCrawlTask", mapOf("url" to data.target)) {
            coroutineScope {
                println("Processing crawl ${data.target}")
                val parser = SiteParser.find<Data>(Url(data.target))
                if (parser == null) {
                    println("No parser found for ${data.target}")
                    appendMetadata("result", "no_parser")
                    return@coroutineScope
                }

                val crawlerPath = parser.convertURLToCrawl(Url(data.target))
                val path = parser.convertURLToCommon(crawlerPath)

                if (store.exists(path)) {
                    println("Already crawled: ${data.target}")
                    appendMetadata("result", "already_crawled")
                    return@coroutineScope
                }

                val result = parser.parse(this@CrawlerImpl, crawlerPath)

                if (result.isFailure) {
                    println("Failed to parse: ${data.target}")
                    appendMetadata("result", "failed_to_parse")
                    return@coroutineScope
                }

                val r = result.getOrThrow()

                r.second.forEach {
                    scope.launch { // finalize on other scope to ensure the higher throughput
                        taskInserter.addCrawlTask(it.toString())
                    }
                }

                r.third.forEach {
                    scope.launch { // finalize on other scope to ensure the higher throughput
                        taskInserter.addListRelatedPagesTask(it)
                    }
                }

                if (result.getOrThrow().first == null) {
                    println("No data: ${data.target}")
                    appendMetadata("result", "no_data")
                    return@coroutineScope
                }

                store.put(path, result.getOrThrow().first!!.body)
                println("Crawled: ${data.target}")
                appendMetadata("result", "success")
            }
        }
    }

    override suspend fun processListRelatedPagesTask(task: ListRelatedPageTask) {
        measured("crawler.processListUserPages", mapOf("url" to task.target)) {
            coroutineScope {
                println("Processing list ${task.target}")
                val parser = SiteParser.registry[task.site]
                if (parser == null) {
                    appendMetadata("result", "no_parser")
                    println("No parser found for listing ${task.target}")
                    return@coroutineScope
                }

                val (links, tasks) = parser.listRelatedPages(this@CrawlerImpl, task.target, task.type)

                appendMetadata("result", "success")
                println("Listed: ${task.target}")

                links.forEach {
                    scope.launch { // finalize on other scope to ensure the higher throughput
                        taskInserter.addCrawlTask(it.toString())
                    }
                }

                tasks.forEach {
                    scope.launch { // finalize on other scope to ensure the higher throughput
                        taskInserter.addListRelatedPagesTask(it)
                    }
                }
            }
        }
    }
}
