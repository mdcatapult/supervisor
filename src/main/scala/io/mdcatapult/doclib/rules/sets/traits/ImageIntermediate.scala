package io.mdcatapult.doclib.rules.sets.traits

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.Envelope

import scala.concurrent.ExecutionContext

trait ImageIntermediate[T <: Envelope] extends SupervisorRule[T] {

  val mimeTypes = List(
    "application/pdf"
  )

  /**
    * Convenience function to automatically test if pdf to image is required and return appropriate sendables
    *
    * @param doc      Document To Test
    * @param config   Config
    * @param registry Registry
    * @return
    */
  def requiredImageIntermediate()(implicit doc: DoclibDoc, config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] =
    if (mimeTypes.contains(doc.mimetype))
      doTask("supervisor.image_intermediate", doc)
    else
      None

}
