package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import cats.data._
import cats.instances.future._
import com.spingo.op_rabbit.SubscriptionRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.rules.{Engine, RulesEngine}
import io.mdcatapult.doclib.rules.legacy.{Engine ⇒ LegacyEngine}
import io.mdcatapult.doclib.rules.sets._
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Queue
import org.bson.types.ObjectId
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

/**
  * RabbitMQ Consumer to handle NER detection using leadmine for documents in MongoDB
  */
object ConsumerSupervisor extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val config: Config = ConfigFactory.load()

  /** initialise queues **/
  val upstream: Queue[SupervisorMsg] = new Queue[SupervisorMsg](config.getString("upstream.queue"))
  val subscription: SubscriptionRef = upstream.subscribe(handle, config.getInt("upstream.concurrent"))

  /** Initialise Mongo **/
  val mongo = new Mongo()
  val collection = mongo.collection

  val engine: RulesEngine =
    if (config.getBoolean("supervisor.legacy"))
      new LegacyEngine()
    else
      new Engine()


  def publish(id: String, sendables: Sendables): Option[Boolean] =
    Try(sendables.foreach(s ⇒ s.send(DoclibMsg(id)))) match {
      case Success(_) ⇒ Some(true)
      case Failure(e) ⇒ throw e
    }

  /**
    * handler for messages from the queue
    * @param msg RabbitMsg
    * @param key String
    * @return
    */
  def handle(msg: SupervisorMsg, key: String): Future[Option[Any]] = {
    (for {
      doc ← OptionT(collection.find(equal("_id", new ObjectId(msg.id))).first().toFutureOption())
      sendables ← OptionT.fromOption(engine.resolve(doc))
      if sendables.nonEmpty
      result ← OptionT.fromOption(publish(doc.getObjectId("_id").toString, sendables))
      if result
      _ ← OptionT.pure(upstream.send(msg))
    } yield {
      println(msg, sendables)
      result
    }).value
  }

}
