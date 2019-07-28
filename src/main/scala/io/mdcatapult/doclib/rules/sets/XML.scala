package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.rules.sets.traits.NER
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

object XML extends NER {


  val validDocuments: List[String] = List(
    "application/rdf+xml",
    "application/smil+xml",
    "application/vnd.google-earth.kml+xml",
    "application/xml",
    "application/xslt+xml",
    "image/svg+xml",
    "model/x3d+xml",
    "text/xml",
    "xml/dtd"
  )

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (doc.contains("mimetype") && validDocuments.contains(doc.getString("mimetype")))
      requiredNer
    else
      None
  }
}
