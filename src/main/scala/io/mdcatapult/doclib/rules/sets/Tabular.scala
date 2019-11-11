package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.{NER, TSVExtract}
import io.mdcatapult.klein.queue.Registry

import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends NER[DoclibMsg] with TSVExtract[DoclibMsg] {


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

  /**
    * If doc is TSV ensure NER is completed first
    * @return
    */
  def doTask(key: String, doc: DoclibDoc)(implicit config: Config, registry: Registry[DoclibMsg]): Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (started(key) && !completed(key))
      Some(Sendables())
    else if (!started(key))
      Some(getSendables(key))
    else
      None
  }

  /**
    * Queue to tsv > ner > analysis
    *
    * @param doc Document to be matched
    * @param config Config
    * @param registry Registry
    * @return Option[Sendables] List of Queue to process this doc
    */
  def unapply(doc: DoclibDoc)
  (implicit config: Config, registry: Registry[DoclibMsg])
        : Option[Sendables] = {
    implicit val document: DoclibDoc = doc

     if (validMimetypes.contains(doc.mimetype)) {
       requiredExtraction match {
         case Some(sendables) ⇒ Some(sendables)
         case _ =>  doNEROrAnalyse
       }
     }
     else None
  }

  /**
    * Do NER or tabular analysis
    * @param doc
    * @param config
    * @param registry
    * @return
    */
  def doNEROrAnalyse()(implicit doc: DoclibDoc, config: Config, registry: Registry[DoclibMsg]): Option[Sendables] =  requiredNer match {
    case Some(sendables) ⇒ Some(sendables)
    case None ⇒ doc.mimetype match {
      case isTsv(_,_) ⇒ doTask("supervisor.tabular.analyse", doc)
      case _ ⇒ doTask("supervisor.tabular.totsv", doc)
    }
  }
}
