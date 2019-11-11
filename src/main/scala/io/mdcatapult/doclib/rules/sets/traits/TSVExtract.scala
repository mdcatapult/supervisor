package io.mdcatapult.doclib.rules.sets.traits

import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

trait TSVExtract[T <: Envelope] extends SupervisorRule[T]{

  /**
    * convenience function to automatically test if TSV extraction required and return appropriate sendables
    * @param doc Document To Test
    * @param config  Config
    * @param registry Registry
    * @return
    */
  def requiredExtraction()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] = {
    if (!started("supervisor.tabular.totsv"))
      Some(getSendables("supervisor.tabular.totsv"))
    else if (!completed("supervisor.tabular.totsv"))
      Some(Sendables())
    else None
  }
}
