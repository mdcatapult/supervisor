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

package io.mdcatapult.doclib.rules

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets._

import scala.concurrent.ExecutionContext

trait RulesEngine {
  def resolve(doc: DoclibDoc): Option[(String, Sendables)]
}

object Engine {
  def apply()(implicit config: Config, sys: ActorSystem, ec: ExecutionContext) =  new Engine
}

/**
  * Engine to determine the queues, exchanges & topics that documents ned to be published to.
  *
  * This effect cascades, if a document qualifies for multiple criteria then it will process each one in
  * sequence over time.
  */
class Engine(implicit config: Config, sys: ActorSystem, ec: ExecutionContext) extends RulesEngine {


  def resolve(doc: DoclibDoc): Option[(String, Sendables)] = doc match {
    case Archive(key, qs) => Some((key, qs.distinct))
    case Tabular(key, qs) => Some((key, qs.distinct))
    case HTML(key, qs) => Some((key, qs.distinct))
    case XML(key, qs) => Some((key, qs.distinct))
    case Text(key, qs) => Some((key, qs.distinct))
    case Document(key, qs) => Some((key, qs.distinct))
    case PDF(key, qs) => Some((key, qs.distinct))
    case Chemical(key, qs) => Some((key, qs.distinct))
    case Image(key, qs) => Some((key, qs.distinct))
    case Audio(key, qs) => Some((key, qs.distinct))
    case Video(key, qs) => Some((key, qs.distinct))
    case Analytical(key, qs) => Some((key, qs.distinct))
    case _ => None
  }
}
