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
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule

import scala.concurrent.ExecutionContext

object Archive extends SupervisorRule[DoclibMsg] {

  val validMimetypes = List(
    "application/gzip",
    "application/vnd.ms-cab-compressed",
    "application/x-7z-compressed",
    "application/x-ace-compressed",
    "application/x-alz-compressed",
    "application/x-apple-diskimage",
    "application/x-arj",
    "application/x-astrotite-afa",
    "application/x-b1",
    "application/x-bzip2",
    "application/x-cfs-compressed",
    "application/x-compress",
    "application/x-cpio",
    "application/x-dar",
    "application/x-dgc-compressed",
    "application/x-gca-compressed",
    "application/x-gtar",
    "application/x-lzh",
    "application/x-lzip",
    "application/x-lzma",
    "application/x-lzop",
    "application/x-lzx",
    "application/x-par2",
    "application/x-rar-compressed",
    "application/x-sbx",
    "application/x-shar",
    "application/x-snappy-framed",
    "application/x-stuffit",
    "application/x-stuffitx",
    "application/x-tar",
    "application/x-xz",
    "application/x-zoo",
    "application/zip"
  )

  def unapply(doc: DoclibDoc)
             (implicit config: Config, m: Materializer, ex: ExecutionContext)
  : Option[(String, Sendables)] = {

    implicit val document: DoclibDoc = doc

    if (!validMimetypes.contains(doc.mimetype))
      None
    else if (completed("supervisor.archive"))
      None
    else
      Some(getSendables("supervisor.archive"))
  }
}
