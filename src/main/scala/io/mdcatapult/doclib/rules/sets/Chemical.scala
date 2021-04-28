package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER
import io.mdcatapult.klein.queue.Registry

import scala.util.matching.Regex

object Chemical extends NER[DoclibMsg] {

  val isChemical: Regex = """(chemical/(.*))""".r

  def resolve(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    if (isChemical.findFirstIn(doc.mimetype).nonEmpty) {
      requiredNer()
    } else {
      None
    }
  }
}
