package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

object Unqualified extends Rule {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor-image")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def unapply(doc: MongoDoc)(implicit config: Config): Option[Sendables] = Some(Sendables())
}
