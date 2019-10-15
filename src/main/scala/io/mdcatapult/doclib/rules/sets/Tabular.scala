package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER
import io.mdcatapult.klein.queue.Registry

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends NER[DoclibMsg] {


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

  def unapply(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[Sendables] = {
    implicit val document: DoclibDoc = doc

    /**
      * If doc is TSV ensure NER is completed first
      * @return
      */
    def doTask(key: String): Option[Sendables] =
      if (started(key) && !completed(key))
        Some(Sendables())
      else if (!started(key))
        Some(getSendables(key))
      else
        None

     if (validMimetypes.contains(doc.mimetype))
       requiredNer match {
        case Some(sendables) ⇒ Some(sendables)
        case None ⇒ doc.mimetype match {
          case isTsv(_,_) ⇒ doTask("supervisor.tabular.analyse")
          case _ ⇒ doTask("supervisor.tabular.totsv")
        }
      }
     else None
  }
}
