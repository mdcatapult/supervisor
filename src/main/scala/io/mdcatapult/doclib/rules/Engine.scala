package io.mdcatapult.doclib.rules

import com.typesafe.config.Config
import io.mdcatapult.doclib.consumers.Workflow
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets._
import io.mdcatapult.klein.queue.Registry

trait RulesEngine {
  def resolve(doc: DoclibDoc): Option[(String, Sendables)]
}


object Engine {
  def apply()(implicit config: Config, workflow: Workflow, registry: Registry[DoclibMsg]) = new Engine
}

/**
  * Engine to determine the queues, exchanges & topics that documents ned to be published to.
  *
  * This effect cascades, if a document qualifies for multiple criteria then it will process each one in
  * sequence over time.
  */
class Engine()(implicit config: Config, workflow: Workflow, registry: Registry[DoclibMsg]) extends RulesEngine {

  def resolve(doc: DoclibDoc): Option[(String, Sendables)] = {

    val stringAndSendablesOpt =
      Archive.resolve(doc) orElse
      Tabular.resolve(doc) orElse
      HTML.resolve(doc) orElse
      XML.resolve(doc) orElse
      Text.resolve(doc) orElse
      Document.resolve(doc) orElse
      PDF.resolve(doc) orElse
      Chemical.resolve(doc) orElse
      Image.resolve(doc) orElse
      Audio.resolve(doc) orElse
      Video.resolve(doc) orElse
      Analytical.resolve(doc)

    for {
      result <- stringAndSendablesOpt
    } yield (result._1, result._2.distinct)
  }
}
