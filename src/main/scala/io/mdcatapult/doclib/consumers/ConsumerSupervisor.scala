package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.SupervisorMsg
import io.mdcatapult.doclib.models.{AppConfig, DoclibDoc}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.util.admin.{Server => AdminServer}
import io.mdcatapult.util.concurrency.SemaphoreLimitedExecution
import org.mongodb.scala._

import scala.util.Try


/**
  * RabbitMQ Consumer to handle NER detection using leadmine for documents in MongoDB
  */
object ConsumerSupervisor extends AbstractConsumer {

  def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo): SubscriptionRef = {
    import as.dispatcher

    AdminServer(config).start()

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.documents-collection"))

    implicit val appConfig: AppConfig =
      AppConfig(
        config.getString("consumer.name"),
        config.getInt("consumer.concurrency"),
        config.getString("consumer.queue"),
        Try(config.getString("consumer.exchange")).toOption
      )


    val upstream: Queue[SupervisorMsg] = queue("consumer.queue")

    val readLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.read-limit"))
    val writeLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.write-limit"))

    upstream.subscribe(
      SupervisorHandler(readLimiter, writeLimiter).handle,
      config.getInt("consumer.concurrency")
    )
  }
}
