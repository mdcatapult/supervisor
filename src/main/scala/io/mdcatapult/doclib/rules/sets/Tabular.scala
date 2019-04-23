package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends Rule {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor-tabular")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val isTabular: Regex =
    """^((text|application)/(csv|tab.*|vnd.(lotus-1-2-3|ms-excel.*|oasis.*|openxmlformats-officedocument.spreadsheetml.*|stardivision.*|sun.xml.calc.*)))$""".r

  def unapply(doc: MongoDoc)(implicit config: Config): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (completed("tabular"))
      None
    else if (started("tabular"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      doc.getString("mimetype") match {
        case isTabular(v, _, _, _) ⇒ Some(withNer(
          Sendables(
            Queue[DoclibMsg]("doclib.tabular")
          )))
        )
        case _ ⇒ None
      }
  }

}
