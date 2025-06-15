package io.github.devngho.geulgyeol.task

import com.sksamuel.hoplite.ConfigLoader
import io.github.devngho.geulgyeol.CrawlTask
import io.github.devngho.geulgyeol.ListRelatedPageTask
import org.koin.core.component.KoinComponent

interface TaskSubscriber: KoinComponent {
    suspend fun subscribe(options: String?)
}