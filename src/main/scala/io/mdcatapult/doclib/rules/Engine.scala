package io.mdcatapult.doclib.rules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets._
import io.mdcatapult.klein.queue.Registry

trait RulesEngine {
  def resolve(doc: DoclibDoc): Option[Sendables]
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

  def resolve(doc: DoclibDoc): Option[Sendables] = doc match {
    case Archive(qs) => Some(qs.distinct)
    case Tabular(qs) => Some(qs.distinct)
    case HTML(qs) => Some(qs.distinct)
    case XML(qs) => Some(qs.distinct)
    case Text(qs) => Some(qs.distinct)
    case Document(qs) => Some(qs.distinct)
    case PDF(qs) => Some(qs.distinct)
    case Chemical(qs) => Some(qs.distinct)
    case Image(qs) => Some(qs.distinct)
    case Audio(qs) => Some(qs.distinct)
    case Video(qs) => Some(qs.distinct)
    case Analytical(qs) => Some(qs.distinct)
    case _ => None
  }
}
