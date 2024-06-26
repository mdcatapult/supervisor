/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mdcatapult.doclib.rules.sets.traits

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.Envelope

import scala.concurrent.ExecutionContext

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
  def requiredBoundingBox()(implicit doc: DoclibDoc, config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] =
    if (boundingBoxMimeTypes.contains(doc.mimetype))
      doTask("supervisor.bounding_box", doc)
    else
      None

}
