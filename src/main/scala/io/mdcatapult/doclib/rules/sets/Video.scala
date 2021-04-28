package io.mdcatapult.doclib.rules.sets

import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule

import scala.util.matching.Regex

object Video extends SupervisorRule[DoclibMsg] {

  val isVideo: Regex = """(video/(.*))""".r

  def resolve(doc: DoclibDoc)
  : Option[(String, Sendables)] =
    if (isVideo.findFirstIn(doc.mimetype).isEmpty) {
      None
    } else {
      None
    }
}
