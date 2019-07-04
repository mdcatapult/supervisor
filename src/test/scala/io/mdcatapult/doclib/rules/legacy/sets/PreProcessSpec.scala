package io.mdcatapult.doclib.rules.legacy.sets

import org.mongodb.scala.bson.BsonString

class PreProcessSpec extends RuleSpec {

  baselineTests(PreProcess, "preprocess", List("mimetype" → BsonString("text/plain")))


}
