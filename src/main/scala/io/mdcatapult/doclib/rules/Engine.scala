package io.mdcatapult.doclib.rules

import com.typesafe.config.Config
import org.mongodb.scala.{Document ⇒ MongoDoc}
import io.mdcatapult.doclib.rules.sets._


/**
  * Engine to determine the queues, exchanges & topics that documents ned to be published to.
  *
  * This effect cascades, if a document qualifies for multiple criteria then it will process each one in
  * sequence over time.
  *
  *
  * @param doc
  * @param config
  */
class Engine(doc: MongoDoc)(implicit config: Config) {
  def resolve: Option[Sendables] = doc match {
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
  def apply(doc: MongoDoc)(implicit config: Config) =  new Engine(doc)
}
