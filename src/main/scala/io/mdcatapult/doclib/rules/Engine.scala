package io.mdcatapult.doclib.rules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.mongodb.scala.{Document ⇒ MongoDoc}
import io.mdcatapult.doclib.rules.sets._

import scala.concurrent.ExecutionContextExecutor

trait RulesEngine {
  def resolve(doc: MongoDoc): Option[Sendables]
}

/**
  * Engine to determine the queues, exchanges & topics that documents ned to be published to.
  *
  * This effect cascades, if a document qualifies for multiple criteria then it will process each one in
  * sequence over time.
  *
  * @param config
  */
class Engine(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor) extends RulesEngine {
  def resolve(doc: MongoDoc): Option[Sendables] = doc match {
    case Archive(qs) ⇒ Some(qs.distinct)
    case Tabular(qs) ⇒ Some(qs.distinct)
    case HTML(qs) ⇒ Some(qs.distinct)
    case XML(qs) ⇒ Some(qs.distinct)
    case Text(qs) ⇒ Some(qs.distinct)
    case Document(qs) ⇒ Some(qs.distinct)
    case Chemical(qs) ⇒ Some(qs.distinct)

  }
}

object Engine {
  def apply()(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor) =  new Engine
}
