package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue.Sendable
import org.mongodb.scala.bson.BsonBoolean
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

abstract class Rule {

  val config: Config = ConfigFactory.load()

  def completed(flag: String)(implicit doc: MongoDoc): Boolean =
    doc.getOrElse(config.getString("supervisor.flags"), MongoDoc())
      .asDocument()
      .getBoolean(flag, BsonBoolean(false))
      .getValue

  def started(flag: String)(implicit doc: MongoDoc): Boolean =
    doc.getOrElse(config.getString("supervisor.flags"), MongoDoc())
      .asDocument().containsKey(flag)


  def withNer(sendables: Sendables)
             (implicit doc: MongoDoc, config: Config, sys: ActorSystem, ex: ExecutionContextExecutor)
  : List[Sendable[DoclibMsg]] =
    sendables ::: NER.unapply(doc).getOrElse(Sendables())
}
