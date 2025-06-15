package io.github.devngho.geulgyeol.metrics

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

@Serializable
/**
 * send to Grafana cloud with otel format
 */
data class LocalMetrics(
    val interval: Long = 10L,
    val percentiles: List<Double> = listOf(0.5, 0.90, 0.99)
): Metrics {
    override val type: String = "local"

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Transient
    private val metricsScope = CoroutineScope(
        newSingleThreadContext("LocalMetrics"))

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

                // print to console
                buildString {
                    captured.sortedBy { it.name }.groupBy { it.name }.forEach { (name, metrics) ->
                        appendLine("Metrics for $name:")
                        val (avg, max, min) = Triple(
                            metrics.map { it.duration / 1000.0 }.average(),
                            metrics.maxOfOrNull { it.duration / 1000.0 }, metrics.minOfOrNull { it.duration / 1000.0 })
                        // quantile
                        val sorted = metrics.map { it.duration / 1000.0 }.sorted()
                        val qs = percentiles.map { it to sorted[(it * sorted.size).toInt()] }

                        appendLine("  avg: %.2f\tmax: %.2f\tmin: %.2f".format(avg, max, min))
                        qs.forEach { (q, v) -> appendLine("  ${q * 100} percentile: $v") }

                        appendLine("  count: ${metrics.size} (count/s: %.2f)".format(metrics.size / interval.toDouble()))

                        // value count and ratio for each metadata
                        val valuesSample = metrics.let {
                            if (it.count() > 1000) it.take(1000) else it
                        }

                        valuesSample.flatMap { it.metadata.keys }.toSet().forEach { key ->
                            val values = valuesSample.mapNotNull { it.metadata[key] }.toSet()
                            val valueCount = values.groupingBy { it }.eachCount()

                            if (values.count() >= 10) {
                                appendLine("  $key:")
                                valueCount.entries.sortedByDescending { it.value }.take(10).forEach { (value, count) ->
                                    appendLine("    $value: $count (${count * 100.0 / values.size}%)")
                                }
                                appendLine("    ...")
                                return@forEach
                            }

                            appendLine("  $key:")
                            valueCount.forEach { (value, count) ->
                                appendLine("    $value: $count (${count * 100.0 / values.size}%)")
                            }
                        }
                    }
                }.also { println(it) }
            }
        }
    }
}