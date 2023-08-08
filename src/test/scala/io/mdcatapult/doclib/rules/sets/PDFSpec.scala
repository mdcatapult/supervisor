package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.Queue
import io.mdcatapult.util.models.Version
import org.mongodb.scala.bson.ObjectId
import org.scalatest.flatspec.AnyFlatSpecLike
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

class PDFSpec extends TestKit(ActorSystem("PDFSpec", ConfigFactory.parseString(
  """
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with ImplicitSender with AnyFlatSpecLike {

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  flags: "doclib"
      |}
      |supervisor {
      |   tabular: {
      |    totsv: {
      |      required: [{
      |        flag: "tabular.totsv"
      |        route: "tabular.totsv"
      |        type: "queue"
      |      }]
      |    }
      |    analyse {
      |      required: [{
      |        flag: "tabular.analysis"
      |        route: "tabular.analysis"
      |        type: "queue"
      |      }]
      |    }
      |  }
      |  text: {
      |    required: [{
      |       flag: "rawtext"
      |       route: "rawtext"
      |       type: "queue"
      |    }]
      |  }
      |  image_intermediate: {
      |    required: [{
      |      flag: "pdf_intermediates"
      |      route: "pdf_intermediates"
      |      type: "queue"
      |    }]
      |  }
      |  bounding_box: {
      |      required: [{
      |        flag: "bounding_boxes"
      |        route: "pdf_figures"
      |        type: "queue"
      |      }]
      |  }
      |  ner: {
      |    required: [{
      |      flag: "ner.chemblactivityterms"
      |      route: "ner.chemblactivityterms"
      |      type: "queue"
      |    },{
      |      flag: "ner.chemicalentities"
      |      route: "ner.chemicalentities"
      |      type: "queue"
      |    },{
      |      flag: "ner.chemicalidentifiers"
      |      route: "ner.chemicalidentifiers"
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
    source = "dummy.pdf",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )

  "A non pdf doc" should "not be processed" in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = PDF.unapply(d)
    assert(result.isEmpty)
  }

  "A  PDF doc which has not been processed by image intermediates" should "return 1 image intermediate sendable" in {
    val d = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file")
    val (key, result) = PDF.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg, DoclibMsg]]))
    assert(result.forall(s =>
      List("pdf_intermediates")
        .contains(s.asInstanceOf[Queue[DoclibMsg, DoclibMsg]].name)))
  }


  "A  PDF doc which has been converted to image intermediates" should "return 1 bounding box sendable" in {
    val docFlag = DoclibFlag(
      key = "pdf_intermediates",
      version = Version(
        number = "0.0.1",
        major = 0,
        minor = 0,
        patch = 1,
        hash = "1234567890"),
      started = LocalDateTime.now.some,
      ended = Some(LocalDateTime.now)
    )
    val d = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = List(docFlag))
    val (key, result) = PDF.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg, DoclibMsg]]))
    assert(result.forall(s =>
      List("pdf_figures")
        .contains(s.asInstanceOf[Queue[DoclibMsg, DoclibMsg]].name)))
  }

  "A  PDF doc which has image intermediates and bounding boxes" should "return empty sendable" in {
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
    val d = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val result = Document.unapply(d)
    assert(result.isEmpty)
  }

}
