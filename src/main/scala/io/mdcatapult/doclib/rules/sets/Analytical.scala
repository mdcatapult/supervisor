package io.mdcatapult.doclib.rules.sets

import akka.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule

import scala.concurrent.ExecutionContext

object Analytical extends SupervisorRule[DoclibMsg] {

  /**
    * If analytical supervisor is required then send to the analytical supervisor queue
    *
    * @param doc      Document to be matched
    * @param config   Config
    * @param registry Registry
    * @return
    */
  override def unapply(doc: DoclibDoc)(implicit config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] =
    if (config.getBoolean("analytical.supervisor"))
      doTask("supervisor.analytical", doc)
    else
      None
}
