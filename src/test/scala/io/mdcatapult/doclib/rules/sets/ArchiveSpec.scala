package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag, FileAttrs}
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala.bson.ObjectId
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContextExecutor

class ArchiveSpec extends CommonSpec {

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
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global


  val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummt.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )

  "An Archive with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}

  "An un-started Archive" should { "return an unarchive sendable" in {
    val d = dummy.copy(mimetype = "application/gzip")
    val result = Archive.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.unarchive")
  }}

  "An started but incomplete Archive" should { "return empty sendables" in {
    val d = dummy.copy(mimetype = "application/gzip", doclib = List(
      DoclibFlag(
        key = "unarchived",
        version = 2.0,
        hash ="01234567890",
        started = LocalDateTime.now()
    )))
    val result = Archive.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }}

  "An completed Archive" should { "return None" in {
    val d = dummy.copy(mimetype = "application/gzip", doclib = List(
      DoclibFlag(
        key = "unarchived",
        version = 2.0,
        hash ="01234567890",
        started = LocalDateTime.now(),
        ended = Some(LocalDateTime.now())
      )))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}
}
