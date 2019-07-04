package io.mdcatapult.doclib.rules.legacy.sets

import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.collection.immutable.Document

class ArchiveSpec extends RuleSpec {

  baselineTests(Archive, "extraction", List("mimetype" → BsonString("application/zip")))

  "A Document with unrecognised mimetype" should " return None" in {
    assert(Archive.unapply(Document("mimetype" → "moo")).isEmpty)
  }


}
