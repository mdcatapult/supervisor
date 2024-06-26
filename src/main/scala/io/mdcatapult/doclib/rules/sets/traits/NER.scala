package io.mdcatapult.doclib.rules.sets.traits

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.Envelope

import scala.concurrent.ExecutionContext

trait NER[T <: Envelope] extends SupervisorRule[T]{

  /**
    * convenience function to automatically test if NER required and return appropriate sendables
    * @param doc Document To Test
    * @param config  Config
    * @param registry Registry
    * @return
    */
  def requiredNer()(implicit doc: DoclibDoc, config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] = {
    doTask("supervisor.ner", doc)
  }

}
