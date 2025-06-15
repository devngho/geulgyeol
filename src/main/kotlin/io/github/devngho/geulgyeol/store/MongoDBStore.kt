package io.github.devngho.geulgyeol.store

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.github.devngho.geulgyeol.data.Data
import io.github.devngho.geulgyeol.metrics.Metrics.Companion.measuredCatching
import io.ktor.http.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bson.types.ObjectId
import java.util.concurrent.TimeUnit
import kotlin.getOrDefault

@Serializable
data class MongoDBStore(
    val connectionString: String,
    val database: String,
    val collection: String,
    val linksCollection: String
): Store {
    @Serializable
    data class Page(
        val url: String,
        val data: Data,
        @SerialName("_id") // Use instead of @BsonId
        @Contextual val id: ObjectId? = null,
    )

    @Serializable
    data class Links(
        val url: String,
        val links: List<String>,
        @SerialName("_id") // Use instead of @BsonId
        @Contextual val id: ObjectId? = null,
    )

    override val type: String = "mongodb"
    @Transient
    private val mongoClient = MongoClient.create(connectionString).withTimeout(10, TimeUnit.SECONDS)
    @Transient
    private val db = mongoClient.getDatabase(database).getCollection<Page>(collection)
    @Transient
    private val linksDb = mongoClient.getDatabase(database).getCollection<Links>(linksCollection)

    @Transient
    private val listCache = mutableSetOf<String>() // we'll assume the files are never deleted
    private val listCacheLock = Mutex()

    @Transient
    private val linksCache = mutableSetOf<String>()
    private val linksCacheLock = Mutex()

    private fun filterUrl(url: Url) = Filters.eq("url", url.toString())
    private fun MongoCollection<Page>.findUrl(url: Url) = find(filterUrl(url))

    override suspend fun initialize(): Result<Unit> = runCatching {
        println("Checking connection...")

        db.find().limit(1).toList()

        println("Connection successful!")

        Unit
    }

    override suspend fun exists(key: Url): Boolean = measuredCatching("mongodb.exists", mapOf("url" to key.toString())) {
        if (listCache.contains(key.toString())) return@measuredCatching true

        (db.findUrl(key).limit(1).count() > 0).also { listCacheLock.withLock {
            if (it) listCache.add(key.toString())
            else listCache.remove(key.toString())
        } }
    }.getOrDefault(false)

    override suspend fun linksExists(key: String): Boolean = measuredCatching("mongodb.linksExists", mapOf("url" to key)) {
        if (linksCache.contains(key)) return@measuredCatching true

        (linksDb.find(Filters.eq("url", key)).limit(1).count() > 0).also {
            linksCacheLock.withLock {
                if (it) linksCache.add(key)
                else linksCache.remove(key)
            }
        }
    }.getOrDefault(false)

    override suspend fun get(key: Url): Result<Data> = measuredCatching("mongodb.get", mapOf("url" to key.toString())) {
        db.findUrl(key).limit(1).toList().first().data
    }

    override suspend fun getLinks(key: String): Result<List<Url>> = measuredCatching("mongodb.getLinks", mapOf("url" to key)) {
        linksDb.find(Filters.eq("url", key.toString())).limit(1).toList().first().links.map { Url(it) }
    }

    override suspend fun put(key: Url, data: Data): Result<Unit> = measuredCatching("mongodb.put", mapOf("url" to key.toString())) {
        db.insertOne(Page(key.toString(), data))

        listCacheLock.withLock {
            listCache.add(key.toString())
        }

        Unit
    }

    override suspend fun putLinks(key: String, links: List<Url>): Result<Unit> = measuredCatching("mongodb.putLinks", mapOf("url" to key)) {
        linksDb.insertOne(Links(key, links.map { it.toString() }))

        linksCacheLock.withLock {
            linksCache.add(key)
        }

        Unit
    }

    override suspend fun delete(key: Url): Result<Unit> = measuredCatching("mongodb.delete", mapOf("url" to key.toString())) {
        db.deleteOne(Filters.eq("url", key.toString()))

        listCacheLock.withLock {
            listCache.remove(key.toString())
        }

        Unit
    }

    override suspend fun deleteLinks(key: String): Result<Unit> = measuredCatching("mongodb.deleteLinks", mapOf("url" to key)) {
        linksDb.deleteOne(Filters.eq("url", key))

        linksCacheLock.withLock {
            linksCache.remove(key)
        }

        Unit
    }

    fun allDocumentsFlow() = db.find()
}