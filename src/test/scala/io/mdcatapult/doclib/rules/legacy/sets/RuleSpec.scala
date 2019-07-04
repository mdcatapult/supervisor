package io.mdcatapult.doclib.rules.legacy.sets

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.{Rule, Sendables}
import io.mdcatapult.klein.queue.Exchange
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonBoolean, BsonDocument, BsonNull, BsonValue}
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor

class RuleSpec extends FlatSpec{
  implicit val config: Config = ConfigFactory.parseMap(Map[String, Any](
    "supervisor.flags" → "doclib",
    "unarchive.to.path" → "./test"
  ).asJava)
  implicit val system: ActorSystem = ActorSystem("scalatest", config)
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global


  def baselineTests(r: Rule, flag: String, props: Seq[(String, BsonValue)] = List[(String, BsonValue)]()): Unit = {
    "An empty (invalid) Document" should " return None" in {
      assert(PreProcess.unapply(Document()).isEmpty)
    }

    "A valid Document with a NULL flag " should "return an empty Some(Sendable)" in {
      val d = Document(List("doclib" → BsonDocument(flag → BsonNull())) ++ props)
      val result = r.unapply(d)
      assert(result.isDefined)
      assert(result.get.isInstanceOf[Sendables])
      assert(result.get.isEmpty)
    }

    "A valid Document with a TRUE flag " should " return None" in {
      val d = Document(List("doclib" → BsonDocument(flag → BsonBoolean(true))) ++ props)
      val result = r.unapply(d)
      assert(result.isEmpty)
    }

    "A valid Document with a FALSE flag " should "None" in {
      val d = Document(List("doclib" → BsonDocument(flag → BsonBoolean(false))) ++ props)
      val result = r.unapply(d)
      assert(result.isEmpty)
    }

    "A valid Document with no flag" should " return Some(Sendable) with at least one exchange" in {
      val d = Document() ++ props
      val result = r.unapply(d)
      assert(result.nonEmpty)
      assert(result.get.nonEmpty)
      assert(result.get.head.isInstanceOf[Exchange[DoclibMsg]])
    }
  }
}
