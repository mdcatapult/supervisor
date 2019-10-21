package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER
import io.mdcatapult.klein.queue.Registry

import scala.concurrent.ExecutionContextExecutor

object Document extends NER[DoclibMsg] {

  val validMimetypes: List[String] = List(
    "application/msword",
    "application/pdf",
    "application/rtf",
    "application/vnd.apple.pages",
    "application/vnd.ms-cab-compressed",
    "application/vnd.ms-powerpoint",
    "application/vnd.oasis.opendocument.chart",
    "application/vnd.oasis.opendocument.database",
    "application/vnd.oasis.opendocument.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.sun.xml.draw.template",
    "application/vnd.sun.xml.impress.template",
    "application/vnd.visio",
    "application/vnd.wordperfect",
    "application/x-appleworks3",
    "application/x-ms-manifest",
    "application/x-ms-pdb",
    "application/x-msaccess"
  )

  def unapply(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (validMimetypes.contains(doc.mimetype))
      requiredNer
    else
      None
  }
}
