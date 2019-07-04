package io.mdcatapult.doclib.rules.legacy.sets

import org.mongodb.scala.bson.BsonString

class NERSpec extends RuleSpec{

  baselineTests(NER, "namedentities", List("source" → BsonString("/path/to/file.txt")))

}
