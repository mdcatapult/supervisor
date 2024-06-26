package io.mdcatapult.doclib.handlers

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.consumer.{AbstractHandler, HandlerResult}
import io.mdcatapult.doclib.flag.MongoFlagContext
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.{AppConfig, DoclibDoc}
import io.mdcatapult.doclib.rules.{Engine, RulesEngine}
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.util.concurrency.LimitedExecution
import io.mdcatapult.util.models.Version
import io.mdcatapult.util.models.result.UpdatedResult
import io.mdcatapult.util.time.nowUtc
import org.apache.pekko.stream.connectors.amqp.scaladsl.CommittableReadResult
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object SupervisorHandler {

  /**
    * SupervisorHandler with an appropriate rule engine based on the supplied config running under the given actor system.
    */
  def apply(readLimiter: LimitedExecution,
            writeLimiter: LimitedExecution)
           (implicit
            as: ActorSystem,
            ec: ExecutionContext,
            config: Config,
            collection: MongoCollection[DoclibDoc],
            appConfig: AppConfig): SupervisorHandler =
    new SupervisorHandler(new Engine(), readLimiter, writeLimiter)
}

case class SupervisorHandlerResult(doclibDoc: DoclibDoc,
                                   doclibMessagesWithConfig: Seq[(Sendable[DoclibMsg], Config)],
                                   publishResult: Future[Boolean]) extends HandlerResult

class SupervisorHandler(engine: RulesEngine,
                        val readLimiter: LimitedExecution,
                        val writeLimiter: LimitedExecution)
                       (implicit
                        ec: ExecutionContext,
                        appConfig: AppConfig,
                        config: Config,
                        collection: MongoCollection[DoclibDoc]) extends AbstractHandler[SupervisorMsg, SupervisorHandlerResult] with LazyLogging {

  class AlreadyQueuedException(docId: ObjectId, flag: String) extends Exception(s"Flag $flag for Document $docId has already been queued")

  /**
    * handler for messages from the queue
    *
    * @param supervisorMessageWrapper incoming message from Rabbit
    * @return
    */
  def handle(supervisorMessageWrapper: CommittableReadResult): Future[(CommittableReadResult, Try[SupervisorHandlerResult])
  ] = {


    Try {
      Json.parse(supervisorMessageWrapper.message.bytes.utf8String).as[SupervisorMsg]
    } match {
      case Success(msg: SupervisorMsg) => {
        logReceived(msg.id)
        val flagContext = new MongoFlagContext(appConfig.name, Version.fromConfig(config), collection, nowUtc)
        val supervisorProcess: Future[Option[SupervisorHandlerResult]] = for {
            doc <- readResetDoc(msg) if doc.nonEmpty
            d = doc.head
            result <- sendMessages(d, msg)
          } yield Option(SupervisorHandlerResult(d, result._1, result._2))
        val finalResult = supervisorProcess.transformWith({
          case Success(Some(value: SupervisorHandlerResult)) => Future((supervisorMessageWrapper, Success(value)))
          case Success(None) => Future ((supervisorMessageWrapper, Failure(new Exception(s"No supervisor result was present for ${msg.id}"))))
          case Failure(e) => Future((supervisorMessageWrapper, Failure(e)))
        })

        postHandleProcess(
          msg.id,
          finalResult,
          flagContext,
          collection
        ).andThen {
          case Success(result) =>
            logger.info(s"Sent ok=${result._2.get.publishResult} messages=${result._2.get.doclibMessagesWithConfig}")
        }
      }
      case Failure(x: Throwable) => Future((supervisorMessageWrapper, Failure(new Exception(s"Unable to decode message received. ${x.getMessage}"))))
    }
  }

  /**
    * forcibly remove status for an exchange/queue to allow reprocessing
    *
    * @return
    */
  def reset(doc: DoclibDoc, msg: SupervisorMsg)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (msg.reset.isDefined) {
      val keys = msg.reset.getOrElse(List[String]())

      val resetDocumentsFuture = Future.sequence {
        keys.map(key => {
          val flags = new MongoFlagContext(key, Version.fromConfig(config), collection, nowUtc)
          flags.reset(doc)
        })
      }

      resetDocumentsFuture.collect { updatedResult =>
        updatedResult.exists(_.changesMade)
      }
    } else {
      Future.successful(true)
    }
  }

  /**
    * Send each sendable to its message queue.
    *
    * @param doc document with id to send
    * @return
    */
  def publish(sendableConfigs: Seq[(Sendable[DoclibMsg], Config)], doc: DoclibDoc): Future[Boolean] = {
    val sendConfirmations = for {
      sendableConfig <- sendableConfigs
      sendConfirmation = sendableConfig._1.send(DoclibMsg(doc._id.toHexString))
    } yield sendConfirmation
    Future.sequence(sendConfirmations).transformWith {
      case Success(list) => Future(true)
      case Failure(e) => Future.failed(e)
    }
  }

  /**
    * Set the flag queued status in the doc to true for each sendable
    *
    * @param sendableConfigs sendable messages paired with their config
    * @param doc             document with id to send
    * @return
    */
  def updateQueueStatus(sendableConfigs: Seq[(Sendable[DoclibMsg], Config)], doc: DoclibDoc): Future[Seq[UpdatedResult]] = {
    Future.sequence(sendableConfigs.map(sendableConfig => {
      val key = sendableConfig._2.getString("flag")
      val version: Version = Version("", 0, 0, 0, "")

      val flags = new MongoFlagContext(key, version, collection, nowUtc)
      flags.queue(doc)
    }))
  }

  /**
    * Returns a sequence of sendable message -> queue config for flags that are
    * not currently queued or are reset
    *
    * @param doc document with id to send
    * @param msg incoming message from Rabbit
    * @return
    */
  def sendableConfig(doc: DoclibDoc, msg: SupervisorMsg): Seq[(Sendable[DoclibMsg], Config)] = {
    engine.resolve(doc) match {
      case Some(sendableKey -> sendables) =>

        val configs: Seq[Option[Config]] =
          for {
            s <- sendables
            c = for {
              rc <- routeConfig(s, sendableKey) if canQueue(doc, rc, msg)
            } yield rc
          } yield c

        val scs = sendables.zip(configs)

        scs.flatMap(x => x._2.map(x._1 -> _))
      case None => Nil
    }
  }

  /**
    * Has the flag already been queued. If it has then we cannot re-queue it ie false.
    * If the flag is being reset then we always re-queue
    *
    * @param doc    document to queue
    * @param config route config
    * @return
    */
  def canQueue(doc: DoclibDoc, config: Config, msg: SupervisorMsg): Boolean = {
    val flagName = config.getString("flag")

    msg.reset.exists(_.contains(flagName)) ||
      !doc.getFlag(flagName).exists(_.isQueued)
  }

  /**
    * Find the supervisor config block for a particular queue
    *
    * @param sendable    message that can be sent
    * @param sendableKey flag key of sendable
    * @return Config
    */
  private def routeConfig(sendable: Sendable[DoclibMsg], sendableKey: String): Option[Config] = {
    val flags = config.getConfigList(s"$sendableKey.required").asScala

    flags.find(_.getString("route") == sendable.name)
  }

  /**
    * Find existing doc for supervisor message, reset it and then return updated doc.
    *
    * @param msg incoming message from Rabbit
    * @return Doclib doc read from Mongo
    */
  private def readResetDoc(msg: SupervisorMsg): Future[Option[DoclibDoc]] = {

    def read() =
      collection.find(equal("_id", new ObjectId(msg.id))).first().toFutureOption()

    for {
      doc <- read() if doc.nonEmpty
      _ = doc.foreach(reset(_, msg))
      updatedDoc <- read()
    } yield updatedDoc
  }

  /**
    * Send messages to the appropriate queue and update the queued status for the doclib flags
    *
    * @param d   document wit id to send
    * @param msg incoming message from Rabbit
    * @return
    */
  def sendMessages(d: DoclibDoc, msg: SupervisorMsg): Future[(Seq[(Sendable[DoclibMsg], Config)], Future[Boolean])] = {
    val sc: Seq[(Sendable[DoclibMsg], Config)] = sendableConfig(d, msg)
    val publishResult = publish(sc, d)

    for {
      _ <- updateQueueStatus(sc, d)
    } yield sc -> publishResult
  }

}