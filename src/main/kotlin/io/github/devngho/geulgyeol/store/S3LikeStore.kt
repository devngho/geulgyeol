package io.github.devngho.geulgyeol.store

import io.github.devngho.geulgyeol.data.Data
import io.github.devngho.geulgyeol.util.UrlSerializer
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Serializable
data class S3LikeStore(
    val accessKeyId: String,
    val secretAccessKey: String,
    val bucket: String,
    val region: String,
    val endpoint: String
): Store {
    override val type: String = "s3"

    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(16))

    private val s3Client: S3Client = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    accessKeyId,
                    secretAccessKey
                )
            )
        )
        .endpointOverride(URI.create(endpoint))
        .build()

    private val listCache = mutableSetOf<String>() // we'll assume the files are never deleted
    private val listCacheLock = Mutex()

    suspend fun exists(key: String): Boolean = scope.async {
        if (listCache.contains(key)) return@async true

        runCatching {
            s3Client.headObject { request ->
                request.bucket(bucket)
                request.key(key)
            }
        }.isSuccess.also { listCacheLock.withLock {
            if (it) listCache.add(key)
            else listCache.remove(key)
        } }
    }.await()

    suspend fun get(key: String): Result<String> = runCatching {
        scope.async {
            val response = s3Client.getObject { request ->
                request.bucket(bucket)
                request.key(key)
            }

            response.readAllBytes().toString(Charsets.UTF_8)
        }.await()
    }

    suspend fun put(key: String, data: String): Result<Unit> = runCatching {
        val bytes = data.toByteArray(Charsets.UTF_8)

        scope.async {
            s3Client.putObject({ request ->
                request.bucket(bucket)
                request.key(key)
                request.contentType("application/octet-stream")
                request.contentLength(bytes.size.toLong())
            }, RequestBody.fromBytes(bytes))

            Unit
        }.await()
    }.onSuccess { listCacheLock.withLock {
        listCache.add(key)
    } }

    suspend fun delete(key: String): Result<Unit> = runCatching {
        scope.async {
            s3Client.deleteObject { request ->
                request.bucket(bucket)
                request.key(key)
            }
        }.join()
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