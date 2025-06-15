package io.github.devngho.geulgyeol

import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.retry
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

object TestCrawler: Crawler {
    private val client = HttpClient(CIO)

    override suspend fun subscribe(options: String?) {

    }

    override suspend fun get(url: Url, headers: Map<String, String>?): Result<HttpResponse> = retry(binaryExponentialBackoff(min = 100L, max = 10000L)) { client.get(url) {
        headers {
            headers?.forEach { (key, value) -> append(key, value) }
        }
    }.let { Result.success(it) } }
}