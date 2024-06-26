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
import org.scalatest.flatspec.AnyFlatSpecLike
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

class AnalyticalSupervisorSpec extends TestKit(ActorSystem("AnalyticalSupervisorSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """)))  with ImplicitSender with AnyFlatSpecLike {

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  flags: "doclib"
      |}
      |supervisor {
      |  analytical: {
      |    required: [{
      |      flag: "analytical.supervisor"
      |      route: "analytical.supervisor"
      |      type: "queue"
      |    }]
      |  }
      |}
      |analytical {
      |  supervisor: true
      |}
      |error {
      |  queue = false
      |}
    """.stripMargin).withFallback(ConfigFactory.load())

  implicit val m: Materializer = Materializer(system)

  private val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )

  "A  PDF doc which has image intermediates and bounding boxes" should "return analytical supervisor sendable" in {
    val flags = List(
      DoclibFlag(
        key = "rawtext",
        version = Version(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)
      ),
      DoclibFlag(
        key = "pdf_intermediates",
        version = Version(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)
      ),
      DoclibFlag(
        key = "bounding_boxes",
        version = Version(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)
      )
    )
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val (key, result) = Analytical.unapply(doc).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg, DoclibMsg]]))
    assert(result.forall(s =>
      List("analytical.supervisor")
        .contains(s.asInstanceOf[Queue[DoclibMsg, DoclibMsg]].name)))
  }

  "A JPEG doc which has an existing analytical supervisor flag" should "return analytical supervisor sendable" in {
    val flags = List(
      DoclibFlag(
        key = "analytical.supervisor",
        version = Version(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)
      )
    )
    val doc = dummy.copy(mimetype = "image/jpeg", source = "/dummy/path/to/dummy/file", doclib = flags)
    val (key, result) = Analytical.unapply(doc).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg, DoclibMsg]]))
    assert(result.forall(s =>
      List("analytical.supervisor")
        .contains(s.asInstanceOf[Queue[DoclibMsg, DoclibMsg]].name)))
  }

}
