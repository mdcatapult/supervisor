package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor


object NER extends Rule {

  val routingKey = "doclib.ner.leadmine"
  val exchange = Some("doclib.ner")

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("source"))
      None
    else if (completed("supervisor.ner"))
      None
    else if (started("supervisor.ner"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      Some(getSendables("supervisor.ner"))
  }

}
