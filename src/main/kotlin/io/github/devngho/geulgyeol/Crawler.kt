package io.github.devngho.geulgyeol

import io.ktor.client.statement.*
import io.ktor.http.*
import org.koin.core.component.KoinComponent

interface Crawler: KoinComponent {
    suspend fun processCrawlTask(task: CrawlTask)
    suspend fun processListRelatedPagesTask(task: ListRelatedPageTask)
    suspend fun get(url: Url, headers: Map<String, String>? = null): Result<HttpResponse>
}

