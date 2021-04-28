package io.mdcatapult.doclib.rules.sets

import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule

import scala.util.matching.Regex

object Audio extends SupervisorRule[DoclibMsg] {

  val isAudio: Regex = """(audio/(.*))""".r

  def resolve(doc: DoclibDoc)
  : Option[(String, Sendables)] =
    if (isAudio.findFirstIn(doc.mimetype).isEmpty) {
      None
    } else {
      None
    }
}
