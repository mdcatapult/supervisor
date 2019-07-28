package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.rules.sets.traits.Rule
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util.matching.Regex

object Audio extends Rule {

  val isAudio: Regex = """(audio/(.*))""".r

  def unapply(doc: MongoDoc)
             (implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor)
  : Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (isAudio.findFirstIn(doc.getString("mimetype")).isEmpty)
      None
    else
      None
  }
}
