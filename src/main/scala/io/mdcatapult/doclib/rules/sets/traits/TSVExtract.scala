package io.mdcatapult.doclib.rules.sets.traits

import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

/**
  * Check if spreadsheet formatted document is valid for extraction
  * @tparam T
  */
trait TSVExtract[T <: Envelope] extends SupervisorRule[T]{

  val extractMimetypes = List(
    "text/csv",
    // We are converting to tsv so this doesn't need converted
    //"text/tab-separated-values",
    "application/vnd.lotus-1-2-3",
    "application/vnd.ms-excel",
    "application/vnd.ms-excel.sheet.macroenabled.12",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
    "application/vnd.stardivision.calc",
    "application/vnd.sun.xml.calc",
    "application/vnd.sun.xml.calc.template"
  )

  /**
    * convenience function to automatically test if TSV extraction required and return appropriate sendables
    * @param doc Document To Test
    * @param config  Config
    * @param registry Registry
    * @return
    */
  def requiredExtraction()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] = {
    extractMimetypes.contains(doc.mimetype) match {
      case true => doTask("supervisor.tabular.totsv", doc)
      case false => None
    }
  }
}
