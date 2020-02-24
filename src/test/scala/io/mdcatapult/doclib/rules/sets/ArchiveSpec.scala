package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{ConsumerVersion, DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.{Queue, Registry}
import org.mongodb.scala.bson.ObjectId
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor

class ArchiveSpec extends TestKit(ActorSystem("ArchiveSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """)))  with ImplicitSender with AnyWordSpecLike {

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  flags: "doclib"
      |}
      |supervisor {
      |  archive: {
      |    required: [{
      |      flag: "unarchived"
      |      route: "unarchive"
      |      type: "queue"
      |    }]
      |  }
      |}
      |op-rabbit {
      |  channel-dispatcher = "op-rabbit.default-channel-dispatcher"
      |  default-channel-dispatcher {
      |    type = Dispatcher
      |    executor = "fork-join-executor"
      |    fork-join-executor {
      |      parallelism-min = 2
      |      parallelism-factor = 2.0
      |      parallelism-max = 4
      |    }
      |    throughput = 1
      |  }
      |  connection {
      |    virtual-host = "doclib"
      |    hosts = ["localhost"]
      |    username = "doclib"
      |    password = "doclib"
      |    port = 5672
      |    ssl = false
      |    connection-timeout = 3s
      |  }
      |}
    """.stripMargin)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

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
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].name == "unarchive")
  }}

  "An started but incomplete Archive" should { "return empty sendables" in {
    val d = dummy.copy(mimetype = "application/gzip", doclib = List(
      DoclibFlag(
        key = "unarchived",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
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
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now(),
        ended = Some(LocalDateTime.now())
      )))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}
}
