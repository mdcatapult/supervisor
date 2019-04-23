package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

object Chemical extends Rule {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor-image")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  val isChemical: Regex = """(chemical/(.*))""".r

  def unapply(doc: MongoDoc)(implicit config: Config): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype")) { None }
    else if (isChemical.findFirstIn(doc.getString("mimetype")).isEmpty) { None }
    else if (completed("ner")) { None }
    else if (started("ner")) { Some(Sendables()) } // ensures requeue with supervisor
    else {
      NER.unapply(doc)
    }
  }
}
