package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import org.mongodb.scala.bson.BsonBoolean
import org.mongodb.scala.{Document ⇒ MongoDoc}

abstract class Rule {

  def completed(flag: String)(implicit doc: MongoDoc): Boolean =
    doc.getOrElse("doclib", MongoDoc())
      .asDocument()
      .getBoolean(flag, BsonBoolean(false))
      .getValue

  def started(flag: String)(implicit doc: MongoDoc): Boolean =
    doc.getOrElse("doclib", MongoDoc())
      .asDocument().containsKey(flag)


  def withNer(sendables: Sendables)(implicit doc: MongoDoc, config: Config) = sendables ::: NER.unapply(doc).getOrElse(Sendables())
}
