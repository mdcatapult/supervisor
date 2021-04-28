package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.consumers.Workflow
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER
import io.mdcatapult.klein.queue.Registry

object XML extends NER[DoclibMsg] {

  val stageName = "xml"

  def resolve(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg], workflow: Workflow): Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    val mimetypes = workflow.getMimetypes(stageName)
    if (mimetypes.isDefined && mimetypes.get.contains(doc.mimetype)) {
      requiredNer()
    } else {
      None
    }
  }
}
