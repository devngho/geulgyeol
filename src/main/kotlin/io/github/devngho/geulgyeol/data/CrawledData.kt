package io.github.devngho.geulgyeol.data

import io.github.devngho.geulgyeol.util.UrlSerializer
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class CrawledData<T: Data>(
    val url: @Serializable(with = UrlSerializer::class) Url,
    val body: T,
)
