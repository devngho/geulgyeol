package io.github.devngho.geulgyeol.task

import io.github.devngho.geulgyeol.ListRelatedPageTask
import org.koin.core.component.KoinComponent

interface TaskInserter: KoinComponent {
    suspend fun addCrawlTask(url: String)
    suspend fun addListRelatedPagesTask(task: ListRelatedPageTask)
}