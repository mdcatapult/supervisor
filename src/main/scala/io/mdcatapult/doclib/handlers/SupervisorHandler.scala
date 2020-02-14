package io.mdcatapult.doclib.handlers

import akka.actor.ActorSystem
import cats.data._
import cats.instances.future._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.doclib.rules.{Engine, RulesEngine}
import io.mdcatapult.doclib.util.DoclibFlags
import io.mdcatapult.klein.queue.{Queue, Sendable}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

class SupervisorHandler(upstream: Sendable[SupervisorMsg])
                       (implicit as: ActorSystem, ec: ExecutionContext, config: Config, collection: MongoCollection[DoclibDoc]) extends LazyLogging {

  /**
    * construct the appropriate rule engine based on the supplied config
    */
  val engine: RulesEngine = new Engine()

  /**
    * forcibly remove status for an exchange/queue to allow reprocessing
    * @return
    */
  def reset(doc: DoclibDoc, msg: SupervisorMsg)(implicit ec: ExecutionContext): Future[List[Option[UpdateResult]]] = {
    if (msg.reset.isDefined) {
      Future.sequence(msg.reset.getOrElse(List[String]()).map(flag ⇒ {
        val doclibFlag = new DoclibFlags(flag)
        doclibFlag.reset(doc)
      }))
    } else {
      Future.successful(List(None))
    }
  }

  /**
    * send a message to all lf the listed Sendabled
    * @param id document id to send
    * @param sendables list of sendables
    * @return
    */
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
      _ ← OptionT.liftF(reset(doc, msg))
      sendables ← OptionT.fromOption(engine.resolve(doc))
      pResult ← OptionT.fromOption(publish(doc._id.toHexString, sendables))
    } yield (sendables, pResult)).value.andThen({
      case Success(r) ⇒ logger.info(s"Processed ${msg.id}. Sent ${r.getOrElse("no messages downstream.")}")
      case Failure(e) ⇒ throw e
    })
  }
}
