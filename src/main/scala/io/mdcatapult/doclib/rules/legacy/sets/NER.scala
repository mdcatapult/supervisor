package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue._
import org.mongodb.scala.bson.BsonInt32
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

object NER extends Rule {

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("source"))
      None
    else if (doc.contains("headers") && doc("headers").asDocument().getInt32("size", BsonInt32(0)).intValue() == 0 )
      None
    else if (completed("namedentities"))
      None
    else if (started("namedentities"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      Some(Sendables(
        Exchange[DoclibMsg](s"${config.getString("doclib.flags")}.namedentities"),
      ))
  }

}
