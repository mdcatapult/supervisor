package io.mdcatapult.doclib.rules.sets

import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule

import scala.util.matching.Regex

object Image extends SupervisorRule[DoclibMsg] {

  val isImage: Regex = """(image/(.*))""".r

  def resolve(doc: DoclibDoc)
  : Option[(String, Sendables)] =
    if (isImage.findFirstIn(doc.mimetype).isEmpty) {
      None
    } else {
      None
    }
}
