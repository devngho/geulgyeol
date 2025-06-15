package io.github.devngho.geulgyeol.metrics

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

@Serializable
/**
 * send to Grafana cloud with otel format
 */
data class GrafanaMetrics(
    val token: String,
    val interval: Long = 30L,
): Metrics {
    override val type: String = "grafana"

    @Transient
    private val metricsScope = CoroutineScope(Dispatchers.Default)

    @Transient
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private val metricsMutex = Mutex()

    data class SingleMetric(
        val name: String,
        val end: Long,
        val duration: Long, /* milliseconds */
        val metadata: Map<String, String>,
    )

    @Transient
    private val metrics = mutableListOf<SingleMetric>()

    override suspend fun <T> measured(
        name: String,
        metadata: Map<String, String>,
        block: suspend Metrics.MetadataAppendable.() -> T
    ): T {
        val metadata = metadata.toMutableMap()

        val start = System.currentTimeMillis()

        val r = (object : Metrics.MetadataAppendable {
            override fun appendMetadata(key: String, value: String) {
                metadata[key] = value
            }
        }).run { block() }

        val end = System.currentTimeMillis()
        val duration = end - start
        metricsMutex.withLock {
            metrics.add(SingleMetric(name, end, duration, metadata))
        }

        return r
    }

    init {
        metricsScope.launch {
            while (true) {
                delay(interval * 1000)

                val captured = metricsMutex.withLock {
                    metrics.toList().also { metrics.clear() }
                }

                metricsScope.launch {
                    client.post("https://otlp-gateway-prod-ap-northeast-0.grafana.net/otlp/v1/metrics") {
                        headers {
                            append("Authorization", "Bearer $token")
                        }

                        contentType(io.ktor.http.ContentType.Application.Json)

                        setBody(buildJsonObject {
                            putJsonArray("resourceMetrics") {
                                addJsonObject {
                                    putJsonArray("scopeMetrics") {
                                        addJsonObject {
                                            putJsonArray("metrics") {
                                                captured.groupBy { it.name }.forEach { (name, metrics) ->
                                                    addJsonObject {
                                                        put("name", name)
                                                        put("unit", "ms")
                                                        put("description", "")
                                                        putJsonObject("gauge") {
                                                            putJsonArray("dataPoints") {
                                                                metrics.forEach {
                                                                    addJsonObject {
                                                                        put("timeUnixNano", it.end * 1000000)
                                                                        put("asInt", it.duration)

                                                                        putJsonObject("labels") {
                                                                            it.metadata.forEach { (key, value) ->
                                                                                put(key, value)
                                                                            }
                                                                        }
                                                                        putJsonArray("attributes") {
                                                                            it.metadata.forEach { (key, value) ->
                                                                                addJsonObject {
                                                                                    put("key", key)
                                                                                    putJsonObject("value") {
                                                                                        put("stringValue", value)
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    }.let {
                        println(it.status)
                        println(it.bodyAsText())
                    }
                }
            }
        }
    }
}