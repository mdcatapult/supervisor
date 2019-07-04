package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.{Rule, Sendables}
import io.mdcatapult.klein.queue.Exchange
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor


object PreProcess extends Rule {

  val downstream = "klein.preprocess"

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (completed("preprocess"))
      None
    else if (started("preprocess"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      Some(Sendables(Exchange[DoclibMsg](downstream)))
  }
}
