package io.mdcatapult.doclib.rules.sets.traits

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables

import scala.concurrent.ExecutionContextExecutor

trait NER extends SupervisorRule{

  /**
    * convenience function to automatically test if NER required and return appropriate sendables
    * @param doc Document To Test
    * @param config  Config
    * @param sys ActorSystem
    * @param ex ExecutionContextExecutor
    * @return
    */
  def requiredNer()(implicit doc: DoclibDoc, config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    if (!started("supervisor.ner"))
      Some(getSendables("supervisor.ner"))
    else if (!completed("supervisor.ner"))
      Some(Sendables())
    else None
  }

}
