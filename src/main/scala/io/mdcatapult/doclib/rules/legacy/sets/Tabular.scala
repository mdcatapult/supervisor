package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends Rule {

  val validMimetypes = List(
    "text/csv",
    "text/tsv",
    "application/vnd.lotus-1-2-3",
    "application/vnd.ms-excel",
    "application/vnd.ms-excel.sheet.macroenabled.12",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
    "application/vnd.stardivision.calc",
    "application/vnd.sun.xml.calc",
    "application/vnd.sun.xml.calc.template",
  )

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (completed("tabular"))
      None
    else if (started("tabular"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      if (validMimetypes.contains(doc.getString("mimetype"))) {
        Some(Sendables(
          Exchange[DoclibMsg](s"${config.getString("doclib.flags")}.tabular")
        ))
      } else {
        None
      }
  }

}
