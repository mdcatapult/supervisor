package io.mdcatapult.doclib.rules.sets.traits


import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

trait TabularAnalysis[T <: Envelope] extends SupervisorRule[T] {

  /**
    * convenience function to automatically test if tabular analysis is required and return appropriate sendables
    *
    * @param doc      Document To Test
    * @param config   Config
    * @param registry Registry
    * @return
    */
  def requiredAnalysis()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] = {
    if (!started("supervisor.tabular.analyse"))
      Some(getSendables("supervisor.tabular.analyse"))
    else if (!completed("supervisor.tabular.analyse"))
      Some(Sendables())
    else None
  }

}
