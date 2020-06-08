package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{ConsumerVersion, DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.{Queue, Registry}
import org.mongodb.scala.bson.ObjectId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import cats.implicits._

class TabularSpec extends TestKit(ActorSystem("TabularSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """)))  with ImplicitSender with AnyWordSpecLike with Matchers {

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
      |      flag: "pdf_intermediate"
      |      route: "pdf_intermediates"
      |      type: "queue"
      |    }]
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
    """.stripMargin)

  implicit val m: Materializer = Materializer(system)
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

  val dummy: DoclibDoc = DoclibDoc(
    _id = new ObjectId(),
    source = "dummt.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )

  val consumerVersion: ConsumerVersion = ConsumerVersion(
    number = "0.0.1",
    major = 0,
    minor = 0,
    patch = 1,
    hash = "1234567890")


  "A Tabular doc with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Tabular.unapply(d)
    assert(result.isEmpty)
  }}

  "An un-started Tabular TSV doc" should { "return a tsv sendable" in {
    implicit val d: DoclibDoc = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val (key, result) = Tabular.getSendables("supervisor.tabular.totsv")
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.forall(s =>
      List("tabular.totsv")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A Tabular doc with partially completed extraction" should { "return empty sendables" in {
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = consumerVersion,
          started = LocalDateTime.now.some,
          ended = None
        )
      )
    )

    Tabular.unapply(d) should be (None)
  }}

  "An extracted Tabular doc with partially completed analysis" should { "not require analysis" in {
    implicit val d: DoclibDoc = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = consumerVersion,
          started = LocalDateTime.now.some,
          ended = Some(LocalDateTime.now())),
      DoclibFlag(
        key = "ner.chemblactivityterms",
        version = consumerVersion,
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)),
      DoclibFlag(
        key = "ner.chemicalentities",
        version = consumerVersion,
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)),
      DoclibFlag(
        key = "ner.chemicalidentifiers",
        version = consumerVersion,
        started = LocalDateTime.now.some,
        ended = Some(LocalDateTime.now)),
        DoclibFlag(
          key = "tabular.analysis",
          version = consumerVersion,
          started = LocalDateTime.now().some)
      ))

    Tabular.requiredAnalysis() should be(None)
  }}

  "A complete TSV doc" should { "return None" in {
    val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = consumerVersion,
          started = LocalDateTime.now.some,
          ended = Some(LocalDateTime.now)),
        DoclibFlag(
          key = "tabular.analysis",
          version = consumerVersion,
          started = LocalDateTime.now().some,
          ended = Some(LocalDateTime.now())),
      ))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}


  "An analysis complete Tabular doc that has not been extracted" should { "return 1 totsv sendables" in {
    // TODO This might not be a realistic test case but is there for completeness. Should the existing flag be reset. What does it mean
    //  for NER to be done but the spreadsheet not extracted?
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.analysis",
          version = consumerVersion,
          started = LocalDateTime.now().some,
          ended = Some(LocalDateTime.now())),
      ))
    val (key, result) = Tabular.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.head.asInstanceOf[Queue[DoclibMsg]].name == "tabular.totsv")
  }}

  "An extracted spreadsheet" should { "return None" in {
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = consumerVersion,
          started = LocalDateTime.now().some,
          ended = Some(LocalDateTime.now())
        )))
    val result = Tabular.unapply(d)
    assert(result.isEmpty)
  }}

  "A tabular doc which has not been NER'd " should { "return 3 NER sendables" in {
    val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val (key, result) = Tabular.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 3)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.forall(s =>
      List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A  tabular doc which has been NER'd" should { "be analysed and not NER'd" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities",  version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalidentifiers",  version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now))
    )
    val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file", doclib = docNER)
    val (key, result) = Tabular.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.forall(s =>
      List("tabular.analysis")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A text doc" should { "not be analysed" in {
    implicit val d: DoclibDoc = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file")
    val result = Tabular.requiredAnalysis()
    assert(result.isEmpty)

  }}

  "A Tabular doc with completed ner with 2 reset" should {
    "return 2 NER sendables" in {
      val doc: DoclibDoc = dummy.copy(
        mimetype = "text/tab-separated-values",
        source = "/dummy/path/to/dummy/file",
        doclib = List(
          DoclibFlag(
            key = "ner.chemblactivityterms",
            version = consumerVersion,
            started = LocalDateTime.now.some,
            ended = Some(LocalDateTime.now)
          ),
          DoclibFlag(
            key = "ner.chemicalentities",
            version = consumerVersion,
            started = LocalDateTime.now.some,
            ended = Some(LocalDateTime.now),
            reset = Some(LocalDateTime.now.plusMinutes(10))
          ),
          DoclibFlag(
            key = "ner.chemicalidentifiers",
            version = consumerVersion,
            started = LocalDateTime.now.some,
            ended = Some(LocalDateTime.now),
            reset = Some(LocalDateTime.now.plusMinutes(10))
          ),
            DoclibFlag(
            key = "tabular.analysis",
            version = consumerVersion,
            started = LocalDateTime.now.some
          )
        )
      )
      val (key, result) = Tabular.unapply(doc).get
      assert(result.length == 2)
      assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg]]))
      assert(result.forall(s =>
        List("ner.chemicalentities","ner.chemicalidentifiers")
          .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
    }
  }

}
