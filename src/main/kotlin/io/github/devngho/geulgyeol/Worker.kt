package io.github.devngho.geulgyeol

import io.github.devngho.geulgyeol.store.MongoDBStore
import io.github.devngho.geulgyeol.store.Store
import io.github.devngho.geulgyeol.task.TaskInserter
import io.github.devngho.geulgyeol.task.TaskSubscriber
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getOrThrow


class Worker: KoinComponent {
    val options = readLine().takeIf { it?.isNotBlank() == true }

    val store: Store by inject()
    val subscriber: TaskSubscriber by inject()
    val inserter: TaskInserter by inject()

    suspend fun run() {
        withContext(Dispatchers.IO) {
            launch {
                subscriber.subscribe(options)
            }

            launch {
                while (isActive) {
                    val url = readLine() ?: break

                    if (url.startsWith("rel ")) {
                        val (_, site, type, target) = url.split(" ")
                        println("Adding task with relation: $site $type $target")
                        inserter.addListRelatedPagesTask(ListRelatedPageTask(target, type, site))
                    } else if (url.startsWith("dir ")) {
                        val url = url.split(" ")[1]

                        println("Adding task: $url")
                        inserter.addCrawlTask(url)
                    } else if (url.startsWith("list_again")) {
                        // try to fetch all the list
                        var i = 0
                        (store as MongoDBStore).allDocumentsFlow().collect { try {
                            if (it.url.contains("blog.naver.com")) {
                                val userId = Url(it.url).segments[0]
                                val page = 1
                                val cacheKey = "https://blog.naver.com/$userId/__pages_cache_${page}"
                                if (withTimeoutOrNull (100) {  !store.linksExists(cacheKey) } != false) {
                                    println("Adding task with relation: naver list $userId")
                                    inserter.addListRelatedPagesTask(ListRelatedPageTask("$userId$${page}", "list_user_pages", "naver"))
                                }
                            } else if (it.url.contains("tistory.com")) {
                                val userId = Url(it.url).host.split(".")[0]
                                val page = 1
                                val cacheKey = "https://$userId.tistory.com/__pages_cache_${page}"
                                if (withTimeoutOrNull (100) { !store.linksExists(cacheKey) } != false) {
                                    println("Adding task with relation: tistory list $userId")
                                    inserter.addListRelatedPagesTask(ListRelatedPageTask("$userId$${page}", "list_user_pages", "tistory"))
                                }
                            }

                            i++
                            if (i % 1000 == 0) {
                                println("Processing well")
                                delay(1000)
                                i = 0
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } }
                    }
                }
            }
        }
    }
}
