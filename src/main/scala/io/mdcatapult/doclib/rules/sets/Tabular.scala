package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.{NER, TSVExtract, TabularAnalysis}
import io.mdcatapult.klein.queue.Registry

import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends TSVExtract[DoclibMsg] with TabularAnalysis[DoclibMsg] with NER[DoclibMsg] {


  val isTsv: Regex =
    """^(text/(tab.*))$""".r

  /**
    * Do NER should before analysis
    * @param doc
    * @param config
    * @param registry
    * @return
    */
  def nerOrAnalysis(doc: DoclibDoc)(implicit config: Config, registry: Registry[DoclibMsg]): Option[Sendables] = {
    // NER first then analysis but only on text/tab-*
    implicit val document: DoclibDoc = doc
    if (isTsv.findFirstIn(doc.mimetype).nonEmpty) {
      requiredNer match {
        case Some(sendables) ⇒ Some(sendables)
        case _ => requiredAnalysis
      }
    } else
      None
  }

  /**
    * Queue to tsv > NER > analysis
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

    requiredExtraction match {
      case Some(sendables) ⇒ Some(sendables)
      case _  =>  nerOrAnalysis(doc)
    }
  }

}
