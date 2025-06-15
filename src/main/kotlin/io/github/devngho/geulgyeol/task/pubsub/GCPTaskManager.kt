package io.github.devngho.geulgyeol.task.pubsub

import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.core.InstantiatingExecutorProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.pubsub.v1.Subscriber
import com.google.protobuf.ByteString
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.PubsubMessage
import com.sksamuel.hoplite.ConfigLoader
import io.github.devngho.geulgyeol.CrawlTask
import io.github.devngho.geulgyeol.Crawler
import io.github.devngho.geulgyeol.ListRelatedPageTask
import io.github.devngho.geulgyeol.metrics.Metrics.Companion.measured
import io.github.devngho.geulgyeol.task.TaskInserter
import io.github.devngho.geulgyeol.task.TaskSubscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.TimeoutException

class GCPTaskManager: TaskSubscriber, TaskInserter {
    data class PubSubConfig(
        val projectId: String,
        val subscriptionIdForCrawlTasks: String,
        val subscriptionIdForListRelatedPages: String,
        val topicIdForCrawlTasks: String,
        val topicIdForListRelatedPages: String
    )

    private val config = ConfigLoader().loadConfigOrThrow<PubSubConfig>("config_pubsub.yaml")
    private val crawler: Crawler by inject()
    private val json = Json
    private val ioContext = Dispatchers.IO.limitedParallelism(8)
    private val threadsEach = 8

    private val auth: CredentialsProvider by lazy {
        FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(File("./account.json").inputStream()))
    }

    private val publisherForCrawlTasks by lazy {
        Publisher.newBuilder(config.topicIdForCrawlTasks).setCredentialsProvider(auth).build()
    }
    private val publisherForListRelatedPages by lazy {
        Publisher.newBuilder(config.topicIdForListRelatedPages).setCredentialsProvider(auth).build()
    }

    override suspend fun subscribe(options: String?) {
        val subscriptionNameForCrawlTasks: ProjectSubscriptionName =
            ProjectSubscriptionName.of(config.projectId, config.subscriptionIdForCrawlTasks)
        val subscriptionNameForListUserPages: ProjectSubscriptionName =
            ProjectSubscriptionName.of(config.projectId, config.subscriptionIdForListRelatedPages)

        val receiverOfCrawlTasks =
            MessageReceiver { message: PubsubMessage, consumer: AckReplyConsumer ->
                runBlocking {
                    crawler.processCrawlTask(json.decodeFromString(message.data.toStringUtf8()))
                    consumer.ack()
                }
            }

        val receiverOfListRelatedPages =
            MessageReceiver { message: PubsubMessage, consumer: AckReplyConsumer ->
                runBlocking {
                    crawler.processListRelatedPagesTask(json.decodeFromString(message.data.toStringUtf8()))
                    consumer.ack()
                }
            }

        var subscriberForCrawlTasks: Subscriber? = null
        var subscriberForListRelatedPages: Subscriber? = null
        try {
            if (options == null || options.contains("crawl")) {
                subscriberForCrawlTasks = Subscriber.newBuilder(subscriptionNameForCrawlTasks, receiverOfCrawlTasks).setExecutorProvider(InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(threadsEach).build()).setCredentialsProvider(auth).build()
            }
            if (options == null || options.contains("list")) {
                subscriberForListRelatedPages =
                    Subscriber.newBuilder(subscriptionNameForListUserPages, receiverOfListRelatedPages)
                        .setExecutorProvider(
                            InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(threadsEach).build()
                        ).setCredentialsProvider(auth).build()
            }
            subscriberForCrawlTasks?.startAsync()?.awaitRunning()
            subscriberForListRelatedPages?.startAsync()?.awaitRunning()

            if (subscriberForCrawlTasks != null) println("Listening for messages on $subscriptionNameForCrawlTasks")
            if (subscriberForListRelatedPages != null)println("Listening for messages on $subscriptionNameForListUserPages")

            subscriberForCrawlTasks?.awaitTerminated()
            subscriberForListRelatedPages?.awaitTerminated()
        } catch (_: TimeoutException) {
            subscriberForCrawlTasks?.stopAsync()
            subscriberForListRelatedPages?.stopAsync()
        }
    }

    override suspend fun addCrawlTask(url: String) = measured("crawler.addCrawlTask", mapOf("url" to url)) { coroutineScope {
        async(ioContext) {
            val task = CrawlTask(url)
            publisherForCrawlTasks.publish(PubsubMessage.newBuilder().setData(ByteString.copyFrom(json.encodeToString(task).toByteArray())).build()) // blocking
            appendMetadata("result", "success")
        }
    } }.join()

    override suspend fun addListRelatedPagesTask(task: ListRelatedPageTask) = measured("crawler.addCrawlTask", mapOf("task.target" to task.target, "task.type" to task.type, "task.site" to task.site)) { coroutineScope {
        async(ioContext) {
            publisherForListRelatedPages.publish(PubsubMessage.newBuilder().setData(ByteString.copyFrom(json.encodeToString(task).toByteArray())).build()) // blocking
        }
    } }.join()
}