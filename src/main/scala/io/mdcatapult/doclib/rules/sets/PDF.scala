package io.mdcatapult.doclib.rules.sets

import akka.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.{BoundingBox, ImageIntermediate}

import scala.concurrent.ExecutionContext

/**
  * Sends PDF doc to correct queue for pdf to page image conversion and the calculation
  * of the bounding boxes of images on each of those pages.
  * Conversion to raw text is handled by [[Document]]
  */
object PDF extends ImageIntermediate[DoclibMsg] with BoundingBox[DoclibMsg] {

  def unapply(doc: DoclibDoc)
             (implicit config: Config, m: Materializer, ex: ExecutionContext)
  : Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    requiredImageIntermediate() match {
      case Some(sendables) => Some(sendables)
      case _ =>  requiredBoundingBox()
    }
  }

}
