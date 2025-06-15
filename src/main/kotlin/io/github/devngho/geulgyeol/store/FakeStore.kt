package io.github.devngho.geulgyeol.store

import io.github.devngho.geulgyeol.data.Data
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
data object FakeStore: Store {
    override val type: String = "test"
    override suspend fun exists(key: Url): Boolean = false
    override suspend fun get(key: Url): Result<Data> = Result.failure(Exception("Not implemented"))
    override suspend fun put(key: Url, data: Data): Result<Unit> = Result.success(Unit)
    override suspend fun delete(key: Url): Result<Unit> = Result.success(Unit)
    override suspend fun linksExists(key: String): Boolean = false
    override suspend fun getLinks(key: String): Result<List<Url>> = Result.failure(Exception("Not implemented"))
    override suspend fun putLinks(key: String, links: List<Url>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteLinks(key: String): Result<Unit> = Result.success(Unit)
}