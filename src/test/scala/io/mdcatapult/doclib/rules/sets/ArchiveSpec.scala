package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.consumers.{Rule, Stage, Workflow}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.doclib.rules.Engine
import io.mdcatapult.klein.queue.{Queue, Registry}
import io.mdcatapult.util.models.Version
import org.mongodb.scala.bson.ObjectId
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.LocalDateTime

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
      |  topic-exchange-name = "doclib"
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
      |error {
      |  queue = false
      |}
      |analytical {
      |  name: "analytical.supervisor"
      |}
    """.stripMargin)

  implicit val m: Materializer = Materializer(system)
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

  private val mimeType = "archiver"
  implicit val workflow = Workflow(Array(
    Stage(
      name = "archiver",
      value = Array(
        Rule(
          name = "mimetypes",
          value = Array(mimeType)
        )
      )
    ),
  ))

  val engine = new Engine()

  private val dummy = DoclibDoc(
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
    val result = Archive.resolve(d)
    assert(result.isEmpty)
  }}

  "An Archive with our new config mimetype" should { "not return None " in {
    val d = dummy.copy(mimetype = mimeType)
    val result = Archive.resolve(d)
    assert(result.isDefined)
  }}

  "An un-started Archive" should { "return an unarchive sendable" in {
    val d = dummy.copy(mimetype = mimeType)
    val (key, result) = Archive.resolve(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.head.asInstanceOf[Queue[DoclibMsg]].name == "unarchive")
  }}

  "An started but incomplete Archive" should { "return empty sendables" in {
    val d = dummy.copy(mimetype = mimeType, doclib = List(
      DoclibFlag(
        key = "unarchived",
        version = Version(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now().some
    )))
    val (key, result) = Archive.resolve(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.isEmpty)
  }}

  "An completed Archive" should { "return None" in {
    val d = dummy.copy(mimetype = mimeType, doclib = List(
      DoclibFlag(
        key = "unarchived",
        version = Version(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now().some,
        ended = Some(LocalDateTime.now())
      )))
    val result = Archive.resolve(d)
    assert(result.isEmpty)
  }}
}
