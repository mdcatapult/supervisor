package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import cats.data._
import cats.instances.future._
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.rules.{Engine, RulesEngine}
import io.mdcatapult.doclib.rules.legacy.{Engine ⇒ LegacyEngine}
import io.mdcatapult.doclib.rules.sets._
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.bson.types.ObjectId
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

/**
  * RabbitMQ Consumer to handle NER detection using leadmine for documents in MongoDB
  */
object ConsumerSupervisor extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** Initialise Mongo **/
  implicit val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[Document] = mongo.collection

  /** initialise queues **/
  val upstream: Queue[SupervisorMsg] = new Queue[SupervisorMsg](config.getString("upstream.queue"), Option(config.getString("op-rabbit.topic-exchange-name")))
  val subscription: SubscriptionRef = upstream.subscribe(new SupervisorHandler(upstream).handle, config.getInt("upstream.concurrent"))

}