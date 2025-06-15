package io.github.devngho.geulgyeol

import com.sksamuel.hoplite.ConfigLoader
import io.github.devngho.geulgyeol.store.Store
import io.github.devngho.geulgyeol.store.Store.StoreConfig
import io.github.devngho.geulgyeol.metrics.Metrics.MetricsConfig
import io.github.devngho.geulgyeol.task.CheckedTaskInserter.Companion.checked
import io.github.devngho.geulgyeol.task.TaskInserter
import io.github.devngho.geulgyeol.task.TaskSubscriber
import io.github.devngho.geulgyeol.task.pubsub.GCPTaskManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module


suspend fun main(): Unit = coroutineScope {
    startKoin {
        modules(module {
            single<Crawler> { CrawlerImpl() }
            single<Store> { ConfigLoader().loadConfigOrThrow<StoreConfig>("config_store.yaml").store.also { launch {
                it.initialize()
            } } }
            single { GCPTaskManager() } bind TaskSubscriber::class
            single { get<GCPTaskManager>().checked() } bind TaskInserter::class
            single { ConfigLoader().loadConfigOrThrow<MetricsConfig>("config_metrics.yaml").metrics }
        })
    }

    Worker().run()
}
