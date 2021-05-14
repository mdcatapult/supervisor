package io.mdcatapult.doclib.rules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets._
import io.mdcatapult.klein.queue.Registry

trait RulesEngine {
  def resolve(doc: DoclibDoc): Option[(String, Sendables)]
}

object Engine {
  def apply()(implicit config: Config, sys: ActorSystem) =  new Engine
}

/**
  * Engine to determine the queues, exchanges & topics that documents ned to be published to.
  *
  * This effect cascades, if a document qualifies for multiple criteria then it will process each one in
  * sequence over time.
  */
class Engine(implicit config: Config, sys: ActorSystem) extends RulesEngine {

  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

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
