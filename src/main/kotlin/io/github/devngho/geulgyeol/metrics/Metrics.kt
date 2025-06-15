package io.github.devngho.geulgyeol.metrics

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
sealed interface Metrics: KoinComponent {
    data class MetricsConfig(val metrics: Metrics)

    interface MetadataAppendable {
        fun appendMetadata(key: String, value: String)
    }

    val type: String

    suspend fun <T> measured(name: String, metadata: Map<String, String>, block: suspend MetadataAppendable.() -> T): T

    companion object: KoinComponent {
        private val metrics: Metrics by inject()

        suspend fun <T> measured(name: String, metadata: Map<String, String>, block: suspend MetadataAppendable.() -> T): T {
            return metrics.measured(name, metadata, block)
        }

        suspend fun <T> measuredCatching(name: String, metadata: Map<String, String>, block: suspend MetadataAppendable.() -> T): Result<T> {
            return metrics.measured(name, metadata) {
                val newMetadata = mutableMapOf<String, String>()

                runCatching { block() }.onFailure {
                    if (it is CancellationException) throw it

                    newMetadata["error"] = it.message ?: "Unknown error"
                }.also {
                    newMetadata.forEach { (key, value) -> appendMetadata(key, value) }
                }
            }
        }
    }
}