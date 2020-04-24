package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER
import io.mdcatapult.klein.queue.Registry

import scala.util.matching.Regex

object HTML extends NER[DoclibMsg] {

  val isHtml: Regex = """(text/((x-server-parsed-|webview)*html))""".r

  def unapply(doc: DoclibDoc)(implicit config: Config, registry: Registry[DoclibMsg]): Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (isHtml.findFirstIn(doc.mimetype).nonEmpty)
      requiredNer
    else
      None
  }

}
