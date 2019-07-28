package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.rules.sets.traits.NER
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex


object HTML extends NER {

  val isHtml: Regex = """(text/((x-server-parsed-|webview)*html))""".r

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (doc.contains("mimetype") && isHtml.findFirstIn(doc.getString("mimetype")).nonEmpty)
      requiredNer
    else
      None
  }

}
