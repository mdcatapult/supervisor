package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

object Chemical extends Rule {

  val isChemical: Regex = """(chemical/(.*))""".r

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (isChemical.findFirstIn(doc.getString("mimetype")).isEmpty)
      None
    else
      Some(withNer(Sendables()))

  }
}
