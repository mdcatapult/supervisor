package io.mdcatapult.doclib.rules.sets

import java.util.Date

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala.bson.{BsonArray, BsonDateTime, BsonDocument, BsonDouble, BsonNull, BsonString}
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

class ArchiveSpec extends CommonSpec  {

  implicit override val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  flags: "doclib"
      |}
      |supervisor {
      |  archive: {
      |    required: [{
      |      flag: "unarchived"
      |      route: "doclib.unarchive"
      |      type: "queue"
      |    }]
      |  }
      |}
    """.stripMargin)
  implicit val system: ActorSystem = ActorSystem("scalatest", config)
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  "An Archive with no mimetype" should "return None " in {
    val d = MongoDoc(List("doclib" → BsonArray()))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }

  "An Archive with an unmatched mimetype" should "return None " in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("dummy/mimetype"),
      "doclib" → BsonArray()))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }

  "An un-started Archive" should "return an unarchive sendable" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("application/gzip"),
      "doclib" → BsonArray()))
    val result = Archive.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.unarchive")
  }

  "An started but incomplete Archive" should "return empty sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("application/gzip"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("unarchived"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonNull())
      ))))
    val result = Archive.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }

  "An completed Archive" should "return None" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("application/gzip"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("unarchived"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())
          )
      ))))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }
}
