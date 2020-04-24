package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule
import io.mdcatapult.klein.queue.Registry

import scala.util.matching.Regex

object Video extends SupervisorRule[DoclibMsg] {

  val isVideo: Regex = """(video/(.*))""".r

  def unapply(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[Sendables] =
    if (isVideo.findFirstIn(doc.mimetype).isEmpty)
      None
    else
      None
}
