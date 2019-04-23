package io.mdcatapult.doclib.rules

import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue.Sendable

package object sets {

  type Sendables = List[Sendable[DoclibMsg]]
  def Sendables(xs: Sendable[DoclibMsg]*): List[Sendable[DoclibMsg]] = List[Sendable[DoclibMsg]](xs: _*)
}
