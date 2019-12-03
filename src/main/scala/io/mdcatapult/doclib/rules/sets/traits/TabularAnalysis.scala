package io.mdcatapult.doclib.rules.sets.traits


import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

trait TabularAnalysis[T <: Envelope] extends SupervisorRule[T] {


  val analyseMimetypes = List(
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
    * convenience function to automatically test if tabular analysis is required and return appropriate sendables
    *
    * @param doc      Document To Test
    * @param config   Config
    * @param registry Registry
    * @return
    */
  def requiredAnalysis()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] = {
    analyseMimetypes.contains(doc.mimetype) match {
      case true => doTask("supervisor.tabular.analyse", doc)
      case false => None
    }
  }

}
