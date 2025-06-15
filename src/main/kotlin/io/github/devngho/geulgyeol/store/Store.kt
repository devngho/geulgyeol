package io.github.devngho.geulgyeol.store

import com.sksamuel.hoplite.ConfigLoader
import io.github.devngho.geulgyeol.data.Data
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * A interface for a store that can be used to store data.
 * It represents a file store like S3, local file system, etc.
 */
@Serializable
sealed interface Store {
    data class StoreConfig(val store: Store)

    val type: String

    suspend fun initialize(): Result<Unit> = Result.success(Unit)

    suspend fun exists(key: Url): Boolean
    suspend fun get(key: Url): Result<Data>
    suspend fun put(key: Url, data: Data): Result<Unit>
    suspend fun delete(key: Url): Result<Unit>

    suspend fun linksExists(key: String): Boolean
    suspend fun getLinks(key: String): Result<List<Url>>
    suspend fun putLinks(key: String, links: List<Url>): Result<Unit>
    suspend fun deleteLinks(key: String): Result<Unit>
}