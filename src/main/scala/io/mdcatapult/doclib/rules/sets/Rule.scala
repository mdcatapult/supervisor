package io.mdcatapult.doclib.rules.sets

import org.mongodb.scala.{Document ⇒ MongoDoc}
import org.mongodb.scala.bson.BsonBoolean

abstract class Rule {

  def completed(flag:String)(implicit doc: MongoDoc): Boolean =
    doc.getOrElse("doclib", MongoDoc())
      .asDocument()
      .getBoolean(flag, BsonBoolean(false))
      .getValue

  def started(flag:String)(implicit doc: MongoDoc): Boolean =
    doc.getOrElse("doclib", MongoDoc())
      .asDocument().containsKey(flag)

}
