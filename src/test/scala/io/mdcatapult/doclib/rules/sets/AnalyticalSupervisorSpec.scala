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
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.ExecutionContextExecutor

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
    """.stripMargin).withFallback(ConfigFactory.load())

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

  val dummy = DoclibDoc(
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
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now)
      ),
      DoclibFlag(
        key = "pdf_intermediates",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now)
      ),
      DoclibFlag(
        key = "bounding_boxes",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now)
      )
    )
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val result = Analytical.unapply(doc)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("analytical.supervisor")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }

}
