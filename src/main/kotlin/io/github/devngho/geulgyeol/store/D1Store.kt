package io.github.devngho.geulgyeol.store

import io.github.devngho.geulgyeol.data.Data
import io.github.devngho.geulgyeol.util.UrlSerializer
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

@Serializable
data class D1Store(
    val prefix: String,
    val accountId: String,
    val apikey: String,
    val databases: LinkedHashMap<String, String>
): Store {
    override val type: String = "d1"

    @Transient
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.toHexString()
    }

    private val listCache = mutableSetOf<String>() // we'll assume the files are never deleted
    private val listCacheLock = Mutex()

    private val EXISTS_QUERY = "SELECT EXISTS(SELECT 1 FROM files WHERE url = ?);"
    private val EXISTS_KEY = "EXISTS(SELECT 1 FROM files WHERE url = ?)"
    private val GET_QUERY = "SELECT data FROM files WHERE url = ?;"
    private val PUT_QUERY = "INSERT INTO files (url, data) VALUES (?, ?);"
    private val DELETE_QUERY = "DELETE FROM files WHERE url = ?;"

    @Serializable
    data class SQLQuery(val sql: String, val params: List<String>)

    @Serializable
    data class SQLResponseRow(val meta: JsonObject, val results: List<JsonObject>, val success: Boolean)

    @Serializable
    data class SQLResponse(val result: List<SQLResponseRow>)

    private val String.dbId: String
        get() = databases[this.md5().substring(0, 2)] ?: throw Exception("Database not found")

    suspend fun exists(key: String): Boolean {
        if (listCache.contains(key)) return true

        // first 2 char of md5 hash of the key

        return runCatching {
            httpClient.post("https://api.cloudflare.com/client/v4/accounts/$accountId/d1/database/${key.dbId}/query") {
                contentType(ContentType.Application.Json)

                headers {
                    append("Authorization", "Bearer $apikey")
                }

                setBody(SQLQuery(EXISTS_QUERY, listOf(key)))
            }.body<SQLResponse>().result.first().results.first()[EXISTS_KEY]!!.jsonPrimitive.int == 1
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(false).also { listCacheLock.withLock {
            if (it) listCache.add(key)
            else listCache.remove(key)
        } }
    }

    suspend fun get(key: String): Result<String> = runCatching {
//        scope.async {
//            val response = s3Client.getObject { request ->
//                request.bucket(bucket)
//                request.key(key)
//            }
//
//            response.readAllBytes()
//        }.await()

        (httpClient.post("https://api.cloudflare.com/client/v4/accounts/$accountId/d1/database/${key.dbId}/query") {
            contentType(ContentType.Application.Json)

            headers {
                append("Authorization", "Bearer $apikey")
            }

            setBody(SQLQuery(GET_QUERY, listOf(key)))
        }.body<SQLResponse>().result.first().results.first()["data"]!!.jsonPrimitive.content)
    }

    suspend fun put(key: String, data: String): Result<Unit> = runCatching {
        httpClient.post("https://api.cloudflare.com/client/v4/accounts/$accountId/d1/database/${key.dbId}/query") {
            contentType(ContentType.Application.Json)

            headers {
                append("Authorization", "Bearer $apikey")
            }

            setBody(SQLQuery(PUT_QUERY, listOf(key, data)))
        }

        Unit
    }.onSuccess { listCacheLock.withLock {
        listCache.add(key)
    } }

    suspend fun delete(key: String): Result<Unit> = runCatching {
        httpClient.post("https://api.cloudflare.com/client/v4/accounts/$accountId/d1/database/${key.dbId}/query") {
            contentType(ContentType.Application.Json)

            headers {
                append("Authorization", "Bearer $apikey")
            }

            setBody(SQLQuery(DELETE_QUERY, listOf(key)))
        }

        Unit
    }.onSuccess { listCacheLock.withLock {
        listCache.remove(key)
    } }

    override suspend fun exists(key: Url): Boolean = exists(key.toString())
    override suspend fun get(key: Url): Result<Data> = get(key.toString()).map { Json.decodeFromString(it) }
    override suspend fun put(key: Url, data: Data): Result<Unit> = put(key.toString(), Json.encodeToString(data))
    override suspend fun delete(key: Url): Result<Unit> = delete(key.toString())

    override suspend fun linksExists(key: String): Boolean = exists(key)
    override suspend fun getLinks(key: String): Result<List<@Serializable(with = UrlSerializer::class) Url>> = get(key).map { Json.decodeFromString(it) }
    override suspend fun putLinks(key: String, links: List<@Serializable(with = UrlSerializer::class) Url>): Result<Unit> = put(key, Json.encodeToString(links))
    override suspend fun deleteLinks(key: String): Result<Unit> = delete(key)
}