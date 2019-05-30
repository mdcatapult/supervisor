package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex


object HTML extends Rule {

  val isHtml: Regex = """(text/((x-server-parsed-|webview)*html))""".r

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (isHtml.findFirstIn(doc.getString("mimetype")).isEmpty)
      None
    else if (completed("html.screenshot") && completed("html.render"))
      None
    else if (started("html") || started("html.render"))
      Some(withNer(Sendables())) // ensures requeue with supervisor
    else
    Some(withNer(
      Sendables(
        Queue[DoclibMsg]("doclib.html.screenshot"),
        Queue[DoclibMsg]("doclib.html.render"),
      )
    ))

  }

}
