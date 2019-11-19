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
import io.mdcatapult.klein.queue.Queue
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.{combine, unset}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

class SupervisorHandler(upstream: Queue[SupervisorMsg])
                       (implicit as: ActorSystem, ex: ExecutionContextExecutor, config: Config, collection: MongoCollection[DoclibDoc]) extends LazyLogging {

  /**
    * construct the appropriate rule engine based on the supplied config
    */
  val engine: RulesEngine = new Engine()

  /**
    * forcibly remove status for an exchange/queue to allow reprocessing
    * @return
    */
  def reset(msg: SupervisorMsg): Future[Option[Any]] = {
    if (msg.reset.isDefined) {
      collection.updateOne(
        equal("_id", new ObjectId(msg.id)),
        combine( msg.reset.getOrElse(List[String]()).map(ex ⇒
          unset(f"doclib.$ex")
        ):_* )
      ).toFutureOption()
    } else {
      Future.successful(Some(false))
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
      _ ← OptionT(reset(msg))
      doc ← OptionT(collection.find(equal("_id", new ObjectId(msg.id))).first().toFutureOption())
      sendables ← OptionT.fromOption(engine.resolve(doc))
      pResult ← OptionT.fromOption(publish(doc._id.toHexString, sendables))
    } yield (sendables, pResult)).value.andThen({
      case Success(r) ⇒ logger.info(s"Processed ${msg.id}. Sent ${r.getOrElse("no messages downstream.")}")
      case Failure(e) ⇒ throw e
    })
  }
}
