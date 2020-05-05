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
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class SupervisorHandler()
                       (implicit as: ActorSystem,
                        ec: ExecutionContext,
                        config: Config,
                        collection: MongoCollection[DoclibDoc]) extends LazyLogging {

  class AlreadyQueuedException(docId: ObjectId, flag: String) extends Exception(s"Flag $flag for Document $docId has already been queued")

  /**
    * construct the appropriate rule engine based on the supplied config
    */
  val engine: RulesEngine = new Engine()

  /**
    * forcibly remove status for an exchange/queue to allow reprocessing
    * @return
    */
  def reset(doc: DoclibDoc, msg: SupervisorMsg)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (msg.reset.isDefined) {
      val flags = msg.reset.getOrElse(List[String]())
      Future.sequence(flags.map(flag => {
        val doclibFlag = new DoclibFlags(flag)
        doclibFlag.reset(doc)
      })
      ).map(_.exists(_.isDefined))
    } else {
      Future.successful(true)
    }
  }

  /**
    * Send each sendable to its message queue if not already queued.
    * Set the flag in the doc to queued if a message is sent
    * @param doc document with id to send
    * @param sendables list of sendables
    * @param sendableKey doclib flag for the message
    * @return
    */
  def publish(doc: DoclibDoc, sendables: Sendables, sendableKey: String): Option[Boolean] = {
    val flags = config.getConfigList(s"supervisor.$sendableKey.required").asScala
    Try(sendables.foreach(s => {
      if (flags.filter(r => r.getString("route") == s.name).map(conf => canQueue(doc, conf)).head) {
        s.send(DoclibMsg(doc._id.toHexString))
      }
    })) match {
      case Success(_) => Some(true)
      case Failure(e) => throw e
    }
  }

  def updateQueueStatus(doc: DoclibDoc, sendables: Sendables, sendableKey: String): Future[List[UpdateResult]] = {
    val flags = config.getConfigList(s"supervisor.$sendableKey.required").asScala
    val xs = sendables.map(s => {
      if (flags.filter(r => r.getString("route") == s.name).map(conf => canQueue(doc, conf)).head) {
        new DoclibFlags(flags.filter(r => r.getString("route") == s.name).head.getString("flag")).queue(doc)
      } else {
        Future.successful(None)
      }
    })
    Future.sequence(xs).map(_.flatten)
  }

  /**
    * Has the flag already been queued. If it has then we cannot re-queue it ie false.
    *
    * @param doc
    * @param config
    * @return
    */
  def canQueue(doc: DoclibDoc, config: Config): Boolean =
    !doc.getFlag(config.getString("flag")).exists(_.queued)

  /**
    * handler for messages from the queue
    * @param msg RabbitMsg
    * @param key String
    * @return
    */
  def handle(msg: SupervisorMsg, key: String): Future[Option[Any]] = {
    (for {
      doc <- OptionT(collection.find(equal("_id", new ObjectId(msg.id))).first().toFutureOption())
      _ <- OptionT.liftF(reset(doc, msg))
      updatedDoc <- OptionT(collection.find(equal("_id", new ObjectId(msg.id))).first().toFutureOption())
      (sendableKey, sendables) <- OptionT.fromOption(engine.resolve(updatedDoc))
      pResult <- OptionT.fromOption(publish(updatedDoc, sendables, sendableKey))
      _ <- OptionT.liftF(updateQueueStatus(updatedDoc, sendables, sendableKey))
    } yield (sendables, pResult)).value.andThen({
      case Success(r) => logger.info(s"Processed ${msg.id}. Sent ${r.getOrElse("no messages downstream.")}")
      case Failure(e) => throw e
    })
  }
}
