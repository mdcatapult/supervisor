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
import io.mdcatapult.doclib.rules.sets.traits.{NER, TSVExtract, TabularAnalysis}

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

/**
  * Rule for files that are composed of tabular data
  */
object Tabular extends TSVExtract[DoclibMsg] with TabularAnalysis[DoclibMsg] with NER[DoclibMsg] {


  val isTsv: Regex =
    """^(text/(csv|tab.*))$""".r

  /**
    * Do NER should before analysis
    * @param doc DoclibDoc
    * @param config Config
    * @param registry Registry
    * @return
    */
  def nerOrAnalysis(doc: DoclibDoc)(implicit config: Config, m: Materializer, ex: ExecutionContext): Option[(String, Sendables)] = {
    // NER first then analysis but only on text/tab-*
    implicit val document: DoclibDoc = doc

    isTsv.findFirstIn(doc.mimetype) match {
      case Some(text) => requiredNer() match {
        case Some(sendables) => Some(sendables)
        case _ => requiredAnalysis()
      }
      case _ => None
    }
  }

  /**
    * Queue to tsv > NER > analysis
    *
    * @param doc Document to be matched
    * @param config Config
    * @param registry Registry
    * @return Option[Sendables] List of Queue to process this doc
    */
  def unapply(doc: DoclibDoc)
  (implicit config: Config, m: Materializer, ex: ExecutionContext)
        : Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc

    requiredExtraction() match {
      case Some(sendables) => Some(sendables)
      case _  =>  nerOrAnalysis(doc)
    }
  }

}
