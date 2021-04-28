package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.RawText
import io.mdcatapult.klein.queue.Registry

object Document extends RawText[DoclibMsg] {

  def resolve(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    requiredRawTextConversion() match {
      case Some(sendables) => Some(sendables)
      case _ => None
    }
  }
}
