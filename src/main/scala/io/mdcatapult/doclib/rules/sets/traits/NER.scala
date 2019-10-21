package io.mdcatapult.doclib.rules.sets.traits

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

import scala.concurrent.ExecutionContextExecutor

trait NER[T <: Envelope] extends SupervisorRule[T]{

  /**
    * convenience function to automatically test if NER required and return appropriate sendables
    * @param doc Document To Test
    * @param config  Config
    * @param registry Registry
    * @return
    */
  def requiredNer()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] = {
    if (!started("supervisor.ner"))
      Some(getSendables("supervisor.ner"))
    else if (!completed("supervisor.ner"))
      Some(Sendables())
    else None
  }

}
