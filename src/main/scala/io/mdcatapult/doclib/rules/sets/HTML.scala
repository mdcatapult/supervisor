package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.Archive.{completed, started}
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex


object HTML extends Rule {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor-archive")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val isHtml: Regex = """(text/((x-server-parsed-|webview)*html))""".r

  def unapply(doc: MongoDoc)(implicit config: Config): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype")) { None }
    else if (isHtml.findFirstIn(doc.getString("mimetype")).isEmpty) { None }
    else if (completed("html.screenshot") && completed("html.render") && completed("ner")) { None }
    else if (started("html") || started("html.render") || started("ner")) { Some(Sendables()) } // ensures requeue with supervisor
    else {
      Some(
        Sendables(
          Queue[DoclibMsg]("doclib.html.screenshot"),
          Queue[DoclibMsg]("doclib.html.render"),
        ) ::: NER.unapply(doc).getOrElse(Sendables())
      )
    }
  }

}
