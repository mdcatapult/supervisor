package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

object Image extends SupervisorRule {

  val isImage: Regex = """(image/(.*))""".r

  def unapply(doc: DoclibDoc)
             (implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor)
  : Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (isImage.findFirstIn(doc.mimetype).isEmpty)
      None
    else
      None
  }
}
