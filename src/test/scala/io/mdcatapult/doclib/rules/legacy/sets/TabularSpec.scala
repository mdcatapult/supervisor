package io.mdcatapult.doclib.rules.legacy.sets

import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.collection.immutable.Document

class TabularSpec extends RuleSpec {

  baselineTests(Tabular, "tabular", List("mimetype" → BsonString("text/csv")))

  "A Document with unrecognised mimetype" should " return None" in {
    assert(Archive.unapply(Document("mimetype" → "moo")).isEmpty)
  }

}
