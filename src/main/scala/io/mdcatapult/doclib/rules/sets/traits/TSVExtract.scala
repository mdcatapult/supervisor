package io.mdcatapult.doclib.rules.sets.traits

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.Envelope

import scala.concurrent.ExecutionContext

/**
  * Check if spreadsheet formatted document is valid for extraction
  * @tparam T Envelope
  */
trait TSVExtract[T <: Envelope] extends SupervisorRule[T]{

  val extractMimetypes = List(
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
  def requiredExtraction()(implicit doc: DoclibDoc, config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] = {
    if (extractMimetypes.contains(doc.mimetype)) {
      doTask("supervisor.tabular.totsv", doc)
    } else {
      None
    }
  }
}
