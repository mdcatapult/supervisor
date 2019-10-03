package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

object Chemical extends NER {

  val isChemical: Regex = """(chemical/(.*))""".r

  def unapply(doc: DoclibDoc)
             (implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor)
  : Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (isChemical.findFirstIn(doc.mimetype).nonEmpty)
      requiredNer
    else
      None
  }
}
