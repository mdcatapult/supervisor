package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.{TSVExtract, TabularAnalysis}
import io.mdcatapult.klein.queue.Registry

import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends TSVExtract[DoclibMsg] with TabularAnalysis[DoclibMsg] {


//  val isTsv: Regex =
//    """^(text/(csv|tab.*))$""".r

//  /**
//    * If doc is TSV ensure NER is completed first
//    * @return
//    */
//  def doTask(key: String, doc: DoclibDoc)(implicit config: Config, registry: Registry[DoclibMsg]): Option[Sendables] = {
//    implicit val document: DoclibDoc = doc
//    if (started(key) && !completed(key))
//      Some(Sendables())
//    else if (!started(key))
//      Some(getSendables(key))
//    else
//      None
//  }

  /**
    * Queue to tsv > analysis
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
      case _ =>  requiredAnalysis
    }
  }

}
