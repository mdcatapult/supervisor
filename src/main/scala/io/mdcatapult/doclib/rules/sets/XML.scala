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

package io.mdcatapult.doclib.rules.sets

import org.apache.pekko.stream.Materializer
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.NER

import scala.concurrent.ExecutionContext

object XML extends NER[DoclibMsg] {


  val validDocuments: List[String] = List(
    "application/rdf+xml",
    "application/smil+xml",
    "application/vnd.google-earth.kml+xml",
    "application/xml",
    "application/xslt+xml",
    "image/svg+xml",
    "model/x3d+xml",
    "text/xml",
    "xml/dtd"
  )

  def unapply(doc: DoclibDoc)
             (implicit config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    if (validDocuments.contains(doc.mimetype))
      requiredNer()
    else
      None
  }
}
