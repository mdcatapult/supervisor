package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.SupervisorMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala._

/**
  * RabbitMQ Consumer to handle NER detection using leadmine for documents in MongoDB
  */
object ConsumerSupervisor extends AbstractConsumer("consumer-supervisor") {

  def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo): SubscriptionRef = {
    import as.dispatcher

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.database.getCollection(config.getString("mongo.collection"))

    /** initialise queues **/
    val upstream =
      Queue[SupervisorMsg](
        config.getString("upstream.queue"),
        Option(config.getString("op-rabbit.topic-exchange-name"))
      )

    upstream.subscribe(
      new SupervisorHandler().handle,
      config.getInt("upstream.concurrent")
    )
  }

}
