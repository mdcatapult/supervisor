package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.SupervisorMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala._

import scala.concurrent.ExecutionContextExecutor

/**
  * RabbitMQ Consumer to handle NER detection using leadmine for documents in MongoDB
  */
object ConsumerSupervisor extends AbstractConsumer("consumer-supervisor") {

  def start()(implicit as: ActorSystem, materializer: ActorMaterializer, mongo: Mongo): SubscriptionRef = {
    implicit val ex: ExecutionContextExecutor = as.dispatcher
    implicit val collection: MongoCollection[DoclibDoc] = mongo.database.getCollection(config.getString("mongo.collection"))
    /** initialise queues **/
    val upstream: Queue[SupervisorMsg] = new Queue[SupervisorMsg](config.getString("upstream.queue"), Option(config.getString("op-rabbit.topic-exchange-name")))
    upstream.subscribe(new SupervisorHandler(upstream).handle, config.getInt("upstream.concurrent"))
  }



}