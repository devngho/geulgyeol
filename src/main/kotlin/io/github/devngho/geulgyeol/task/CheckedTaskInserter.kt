package io.github.devngho.geulgyeol.task

import io.github.devngho.geulgyeol.ListRelatedPageTask
import io.github.devngho.geulgyeol.data.Data
import io.github.devngho.geulgyeol.store.Store
import io.github.devngho.geulgyeol.site.SiteParser
import io.ktor.http.Url
import org.koin.core.component.inject

class CheckedTaskInserter(val taskInserter: TaskInserter): TaskInserter by taskInserter {
    private val store: Store by inject()

    override suspend fun addCrawlTask(url: String) {
        if (store.exists(Url(url))) {
            return
        }

        taskInserter.addCrawlTask(url)
    }

    override suspend fun addListRelatedPagesTask(task: ListRelatedPageTask) {
        if (!SiteParser.registry.contains(task.site)) return

        taskInserter.addListRelatedPagesTask(task)
    }

    companion object {
        fun TaskInserter.checked(): TaskInserter = CheckedTaskInserter(this)
    }
}