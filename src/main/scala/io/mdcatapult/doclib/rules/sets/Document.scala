package io.mdcatapult.doclib.rules.sets

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.RawText

import scala.concurrent.ExecutionContext

object Document extends RawText[DoclibMsg] {

  def unapply(doc: DoclibDoc)
             (implicit config: Config, m: Materializer, ex: ExecutionContext)
  : Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    requiredRawTextConversion() match {
      case Some(sendables) => Some(sendables)
      case _ =>  None
    }
  }
}
