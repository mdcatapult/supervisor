package io.mdcatapult.doclib.rules.legacy

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.rules.RulesEngine
import io.mdcatapult.doclib.rules.legacy.sets._
import io.mdcatapult.doclib.rules.sets.Sendables
import org.mongodb.scala.{Document ⇒ MongoDoc}
import scala.concurrent.ExecutionContextExecutor


/**
  * Engine to determine the queues, exchanges & topics that documents ned to be published to.
  *
  * This effect cascades, if a document qualifies for multiple criteria then it will process each one in
  * sequence over time.
  *
  * @param config Config
  */
class Engine(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor) extends RulesEngine {
  def resolve(doc: MongoDoc): Option[Sendables] = doc match {
    case PreProcess(qs) ⇒ Some(qs.distinct)
    case Archive(qs) ⇒ Some(qs.distinct)
    case Tabular(qs) ⇒ Some(qs.distinct)
    case NER(qs) ⇒ Some(qs.distinct)
    case _ ⇒ None
  }
}

object Engine {
  def apply()(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor) =  new Engine()
}
