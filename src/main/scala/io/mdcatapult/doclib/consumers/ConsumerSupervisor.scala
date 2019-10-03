package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
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
object ConsumerSupervisor extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** Initialise Mongo **/
  implicit val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[DoclibDoc] = mongo.database.getCollection(config.getString("mongo.collection"))

  /** initialise queues **/
  val upstream: Queue[SupervisorMsg] = new Queue[SupervisorMsg](config.getString("upstream.queue"))
  val subscription: SubscriptionRef = upstream.subscribe(new SupervisorHandler(upstream).handle, config.getInt("upstream.concurrent"))

}