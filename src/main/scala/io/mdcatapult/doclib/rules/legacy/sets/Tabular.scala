package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}
import io.mdcatapult.doclib.rules.sets.{Rule, Sendables}
import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends Rule {
  /**
    * regex not including tsv on purpose
    */
  val isTabular: Regex =
    """^((application)/(vnd.(lotus-1-2-3|ms-excel.*|oasis.*|openxmlformats-officedocument.spreadsheetml.*|stardivision.*|sun.xml.calc.*)))$""".r

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (completed("tabular"))
      None
    else if (started("tabular"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      doc.getString("mimetype") match {
        case isTabular(v, _, _, _) ⇒ Some(
          Sendables(
            Exchange[DoclibMsg](s"${config.getString("supervisor.flags")}.tabular")
          ))
        case _ ⇒ None
      }
  }
}
