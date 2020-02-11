package io.mdcatapult.doclib.rules

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{ConsumerVersion, DoclibDoc, DoclibFlag}
import io.mdcatapult.doclib.rules.sets._
import io.mdcatapult.klein.queue.{Queue, Registry}
import org.mongodb.scala.bson.ObjectId
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.ExecutionContextExecutor

class EngineSpec extends TestKit(ActorSystem("EngineSpec", ConfigFactory.parseString(
  """
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with ImplicitSender with AnyFlatSpecLike {

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
    """.stripMargin).withFallback(ConfigFactory.load())

  // Some of the RawText.convertMimetypes are handled by other rules first eg spreadsheets and archive
  val rawTextConversions = List(
    "application/msword",
    "application/pdf",
    "application/rtf",
    "application/vnd.apple.pages",
    "application/vnd.ms-powerpoint",
    "application/vnd.oasis.opendocument.chart",
    "application/vnd.oasis.opendocument.database",
    "application/vnd.oasis.opendocument.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.sun.xml.draw.template",
    "application/vnd.sun.xml.impress.template",
    "application/vnd.visio",
    "application/vnd.wordperfect",
    "application/x-appleworks3",
    "application/x-ms-manifest",
    "application/x-ms-pdb",
    "application/x-msaccess"
  )

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

  val engine: RulesEngine = new Engine()

  val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )


  "An unknown mimetype" should "return None" in {
    val doc = dummy.copy(mimetype = "dummy/mimetype")
    val result = engine.resolve(doc)
    assert(result.isEmpty)
  }

  "A non tabular spreadsheet doc" should "return a tsv extract" in {
    Tabular.extractMimetypes.foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 1)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("tabular.totsv")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }

  "A tabular doc" should "return NER sendables" in {
    val doc = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val result = engine.resolve(doc)
    assert(result.get.length == 3)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }

  "A text doc" should "return NER sendables" in {
    Text.validDocuments.foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 3)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }

  "An archive doc" should "return archive sendable" in {
    Archive.validMimetypes.foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 1)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("unarchive")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }

  "A document doc" should "return rawtext sendable" in {
    rawTextConversions.foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 1)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("rawtext")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }

  "A PDF document with completed raw text" should "return image intermediates sendable" in {
    val doclibFlags = List(
      DoclibFlag(
        key = "prefetch",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now(),
        ended = Some(LocalDateTime.now)),
      DoclibFlag(
        key = "rawtext",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now))
    )
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = doclibFlags)
    val result = engine.resolve(doc)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("pdf_intermediates")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }

  "A PDF which has been raw text and pdf intermediates processed" should "return bounding boxes sendables" in {
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
      )
    )
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val result = engine.resolve(doc)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("pdf_figures")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }

  "A PDF which has been raw text, pdf intermediates and bounding box processed" should "return None" in {
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
    val result = engine.resolve(doc)
    assert(result.isEmpty)
  }


  "An HTML doc" should "return ner sendables" in {
    // TODO are there more mimeteypes?
    List("text/html").foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 3)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }

  "An image doc" should "return None" in {
    // TODO not a very exhaustive list of image mimetypes
    // Image returns None anyway
    // Note that image/svg+xml is handled by the XML rule
    List("image/png", "image/tiff", "image/webp", "image/bmp", "image/vnd.microsoft.icon").foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.isEmpty)
    })
  }

  "A chemical doc" should "return ner sendables" in {
    // TODO not a very exhaustive list of chemical mimetypes. See https://en.wikipedia.org/wiki/Chemical_file_format
    List("chemical/x-inchi ", "chemical/x-chem3d").foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 3)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }

  "A video doc" should "return None" in {
    // TODO not a very exhaustive list of video mimetypes
    // Video returns None anyway
    List("video/mpeg", "video/mp2t").foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.isEmpty)
    })
  }

  "An audio doc" should "return None" in {
    // TODO not a very exhaustive list of audio mimetypes
    // Audio returns None anyway
    List("audio/wav", "audio/aac").foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.isEmpty)
    })
  }

  "An XML doc" should "return ner sendables" in {
    XML.validDocuments.foreach(mimetype => {
      val doc = dummy.copy(mimetype = mimetype, source = "/dummy/path/to/dummy/file")
      val result = engine.resolve(doc)
      assert(result.get.length == 3)
      assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.get.forall(s ⇒
        List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    })
  }


  "A  PDF doc which has image intermediates and bounding boxes" should "be queued to the analytical supervisor" in {

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
        key = "bounding_box",
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
    val engine: RulesEngine = new Engine()
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val result = engine.resolve(doc)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("analytical.supervisor")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }

}
