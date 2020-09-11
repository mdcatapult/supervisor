package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER
import io.mdcatapult.klein.queue.Registry

object XML extends NER[DoclibMsg] {


  val validDocuments: List[String] = List(
    "application/rdf+xml",
    "application/smil+xml",
    "application/vnd.google-earth.kml+xml",
    "application/xml",
    "application/xslt+xml",
    "image/svg+xml",
    "model/x3d+xml",
    "text/xml",
    "xml/dtd"
  )

  def unapply(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg]): Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    if (validDocuments.contains(doc.mimetype))
      requiredNer()
    else
      None
  }
}
