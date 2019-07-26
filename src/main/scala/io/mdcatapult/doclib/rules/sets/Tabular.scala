package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import cats.data._
import cats.implicits._
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue._
import org.mongodb.scala.{Document ⇒ MongoDoc}
import collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends Rule {


  val isTsv: Regex =
    """^(text/(csv|tab.*))$""".r

  val validMimetypes = List(
    "text/csv",
    "text/tab-separated-values",
    "application/vnd.lotus-1-2-3",
    "application/vnd.ms-excel",
    "application/vnd.ms-excel.sheet.macroenabled.12",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
    "application/vnd.stardivision.calc",
    "application/vnd.sun.xml.calc",
    "application/vnd.sun.xml.calc.template",
  )

  def unapply(doc: MongoDoc)
             (implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor)
  : Option[Sendables] = {
    implicit val document: MongoDoc = doc


    def hasNer = {
      if (!started("supervisor.ner"))
        Some(withNer(Sendables())) // not been to NER yet to lets start that
      else if (!completed("supervisor.ner"))
        Some(Sendables()) // wait for NER to finish
      else None
    }

    /**
      * If doc is TSV ensure NER is completed first
      * @return
      */
    def doTableProcessing: Option[Sendables] =
      if (started("supervisor.tabular.analyse") && !completed("supervisor.tabular.analyse"))
        Some(Sendables())
      else if (!started("supervisor.tabular.analyse"))
        Some(getSendables("supervisor.tabular.analyse"))
      else
        None

    def doConvertToTsv: Option[Sendables] =
      if (started("supervisor.tabular.totsv") && !completed("supervisor.tabular.totsv"))
        Some(Sendables())
      else if (!started("supervisor.tabular.totsv"))
        Some(getSendables("supervisor.tabular.totsv"))
      else
        None

     if (validMimetypes.contains(doc.getString("mimetype")))
       hasNer match {
        case Some(sendables) ⇒ Some(sendables)
        case None ⇒ doc.getString("mimetype") match {
          case isTsv(_,_) ⇒ doTableProcessing
          case _ ⇒ doConvertToTsv
        }
      }
     else None
  }
}
