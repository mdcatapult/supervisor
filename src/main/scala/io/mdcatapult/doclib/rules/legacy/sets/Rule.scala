package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue.Sendable
import io.mdcatapult.doclib.rules.sets.Sendables
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

abstract class Rule {

  def completed(flag: String)(implicit doc: MongoDoc, config: Config): Boolean = {
    val d = doc.getOrElse(config.getString("doclib.flags"), MongoDoc())
      .asDocument()
    if (!d.containsKey(flag)) {
      false
    } else {
      if (d.get(flag).isNull) {
        false
      } else {
        // returns true as anything other than Null is considered complete
        true
      }
    }
  }


  def started(flag: String)(implicit doc: MongoDoc, config: Config): Boolean =
    doc.getOrElse(config.getString("doclib.flags"), MongoDoc())
      .asDocument().containsKey(flag)


  def withNer(sendables: Sendables)
             (implicit doc: MongoDoc, config: Config, sys: ActorSystem, ex: ExecutionContextExecutor)
  : List[Sendable[DoclibMsg]] =
    sendables ::: NER.unapply(doc).getOrElse(Sendables())

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables]
}
