package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.{Rule, Sendables}
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor


object NER extends Rule {

  val exchange = s"${config.getString("supervisor.flags")}.ner"


  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("source"))
      None
    else if (completed("ner"))
      None
    else if (started("ner"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      Some(Sendables(
        Exchange[DoclibMsg](exchange),
      ))
  }

}
