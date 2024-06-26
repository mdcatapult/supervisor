package io.mdcatapult.doclib.rules.sets

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

object Chemical extends NER[DoclibMsg] {

  val isChemical: Regex = """(chemical/(.*))""".r

  def unapply(doc: DoclibDoc)
             (implicit config: Config, m: Materializer, ex: ExecutionContext)
  : Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    if (isChemical.findFirstIn(doc.mimetype).nonEmpty)
      requiredNer()
    else
      None
  }
}
