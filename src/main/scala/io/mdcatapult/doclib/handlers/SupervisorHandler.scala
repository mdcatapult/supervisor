package io.mdcatapult.doclib.handlers

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibDocExtractor}
import io.mdcatapult.doclib.rules.{Engine, RulesEngine}
import io.mdcatapult.doclib.flag.{FlagContext, MongoFlagStore}
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.util.models.Version
import io.mdcatapult.util.models.result.UpdatedResult
import io.mdcatapult.util.time.nowUtc
import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.equal

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object SupervisorHandler {

  /**
    * SupervisorHandler with an appropriate rule engine based on the supplied config running under the given actor system.
    */
  def apply()(implicit as: ActorSystem,
              ec: ExecutionContext,
              config: Config,
              collection: MongoCollection[DoclibDoc]): SupervisorHandler =
    new SupervisorHandler(new Engine())
}

class SupervisorHandler(engine: RulesEngine)
                       (implicit
                        ec: ExecutionContext,
                        config: Config,
                        collection: MongoCollection[DoclibDoc]) extends LazyLogging {

  class AlreadyQueuedException(docId: ObjectId, flag: String) extends Exception(s"Flag $flag for Document $docId has already been queued")

  /**
    * forcibly remove status for an exchange/queue to allow reprocessing
    * @return
    */
  def reset(doc: DoclibDoc, msg: SupervisorMsg)(implicit ec: ExecutionContext): Future[Boolean] = {
    if (msg.reset.isDefined) {
      val keys = msg.reset.getOrElse(List[String]())
      Future.sequence(keys.map(key => {
        val docExtractor = new DoclibDocExtractor(key, java.time.Duration.ofSeconds(10))
        val flags = new MongoFlagStore(Version.fromConfig(config), docExtractor, collection, nowUtc)
        val flagContext: FlagContext = flags.findFlagContext(Some(key))
          flagContext.reset(doc)
        })
      ).map(_.filter(res => res.changesMade)).map(_.nonEmpty)
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
  def publish(sendableConfigs: Seq[(Sendable[DoclibMsg],Config)], doc: DoclibDoc): Boolean = {
    Try(
      sendableConfigs.foreach { s => s._1.send(DoclibMsg(doc._id.toHexString)) }
    ) match {
      case Success(_) => true
      case Failure(e) => throw e
    }
  }

  /**
    * Set the flag queued status in the doc to true for each sendable
    *
    * @param sendableConfigs sendable messages paired with their config
    * @param doc document with id to send
    * @return
    */
  def updateQueueStatus(sendableConfigs: Seq[(Sendable[DoclibMsg],Config)], doc: DoclibDoc): Future[Seq[UpdatedResult]] = {
    Future.sequence(sendableConfigs.map(sendableConfig => {
      val key = sendableConfig._2.getString("flag")
      val docExtractor = new DoclibDocExtractor(key, java.time.Duration.ofSeconds(10))
      val version: Version = Version("",0,0,0,"")
      val store = new MongoFlagStore(version, docExtractor, collection, nowUtc)
      val context = store.findFlagContext(Some(key))
      context.queue(doc)
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
  def sendableConfig(doc: DoclibDoc, msg: SupervisorMsg): Seq[(Sendable[DoclibMsg],Config)] = {

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
    * @param doc document to queue
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
    * @param sendable message that can be sent
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
    * @param d document wit id to send
    * @param msg incoming message from Rabbit
    * @return
    */
  def sendMessages(d: DoclibDoc, msg: SupervisorMsg): Future[(Seq[(Sendable[DoclibMsg], Config)], Boolean)] = {
    val sc: Seq[(Sendable[DoclibMsg], Config)] = sendableConfig(d, msg)
    val publishResult = publish(sc, d)

    for {
      _ <- updateQueueStatus(sc, d)
    } yield sc -> publishResult
  }

  /**
    * handler for messages from the queue
    * @param msg incoming message from Rabbit
    * @param key String
    * @return
    */
  def handle(msg: SupervisorMsg, key: String): Future[Option[Any]] = {

    val updated: Future[(Seq[(Sendable[DoclibMsg], Config)], Boolean)] =
      for {
        doc <- readResetDoc(msg) if doc.nonEmpty
        d = doc.head
        result <- sendMessages(d, msg)
      } yield result

    updated.andThen({
      case Success(r) => logger.info(s"Processed ${msg.id}. Sent ok=${r._2} messages=${r._1}")
      case Failure(e) => throw e
    })

    updated.map(Option.apply)
  }
}
