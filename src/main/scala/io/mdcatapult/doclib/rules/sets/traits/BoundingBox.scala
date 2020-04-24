package io.mdcatapult.doclib.rules.sets.traits

import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

trait BoundingBox[T <: Envelope] extends SupervisorRule[T] {

  val boundingBoxMimeTypes = List(
    "application/pdf"
  )

  /**
    * Convenience function to automatically test if bounding box conversion
    * is required and return appropriate sendables
    *
    * @param doc      Document To Test
    * @param config   Config
    * @param registry Registry
    * @return
    */
  def requiredBoundingBox()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] =
    if (boundingBoxMimeTypes.contains(doc.mimetype))
      doTask("supervisor.bounding_box", doc)
    else
      None

}
