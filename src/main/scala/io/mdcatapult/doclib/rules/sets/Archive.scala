package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.consumers.Workflow
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule
import io.mdcatapult.klein.queue.Registry

object Archive extends SupervisorRule[DoclibMsg] {

  val stageName = "archiver"

  def resolve(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg], workflow: Workflow)
  : Option[(String, Sendables)] = {

    val mimeTypes = workflow.getMimetypes(stageName)

    implicit val document: DoclibDoc = doc

    if (mimeTypes.isEmpty || !mimeTypes.get.contains(doc.mimetype)) {
      None
    } else if (completed("supervisor.archive")) {
      None
    } else {
      Some(getSendables("supervisor.archive"))
    }
  }
}
