package io.github.devngho.geulgyeol

import io.github.devngho.geulgyeol.metrics.Metrics
import io.kotest.core.spec.style.StringSpec

class MetricsTest : StringSpec({
    "Can initialize" {
        println(Metrics.metrics)
    }
})