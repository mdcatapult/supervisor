package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.util.models.Version
import org.mongodb.scala.bson.ObjectId
import org.scalatest.wordspec.AnyWordSpecLike
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

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
      |queue {
      |  max-retries = 3
      |  host = "localhost"
      |  virtual-host = "doclib"
      |  username = "doclib"
      |  password = "doclib"
      |  port = 5672
      |  ssl = false
      |  connection-timeout = 3000
      |}
      |error {
      |  queue = false
      |}
      |analytical {
      |  name: "analytical.supervisor"
      |}
    """.stripMargin)

  implicit val m: Materializer = Materializer(system)

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
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}

  "An un-started Archive" should { "return an unarchive sendable" in {
    val d = dummy.copy(mimetype = "application/gzip")
    val (key, result) = Archive.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.head.isInstanceOf[Queue[DoclibMsg, DoclibMsg]])
    assert(result.head.asInstanceOf[Queue[DoclibMsg, DoclibMsg]].name == "unarchive")
  }}

  "An started but incomplete Archive" should { "return empty sendables" in {
    val d = dummy.copy(mimetype = "application/gzip", doclib = List(
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
    val (key, result) = Archive.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.isEmpty)
  }}

  "An completed Archive" should { "return None" in {
    val d = dummy.copy(mimetype = "application/gzip", doclib = List(
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
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}
}
