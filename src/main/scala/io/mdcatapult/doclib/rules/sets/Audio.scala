package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule
import io.mdcatapult.klein.queue.Registry

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

object Audio extends SupervisorRule[DoclibMsg] {

  val isAudio: Regex = """(audio/(.*))""".r

  def unapply(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (isAudio.findFirstIn(doc.mimetype).isEmpty)
      None
    else
      None
  }
}
