package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.{Queue, Registry}
import io.mdcatapult.util.models.Version
import org.mongodb.scala.bson.ObjectId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import cats.implicits._

class TextSpec extends TestKit(ActorSystem("TextSpec", ConfigFactory.parseString("""
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
      |    trust-everything = true
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

  private val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.pdf",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )

  private val consumerVersion = Version(
    number = "0.0.1",
    major = 0,
    minor = 0,
    patch = 1,
    hash = "1234567890")


  "A doc with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Text.unapply(d)
    assert(result.isEmpty)
  }}

  "A new text doc " should { "return 3 NER sendables" in {
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file")
    val (key, result) = Text.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 3)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.forall(s =>
      List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A  text doc which has been NER'd" should { "not be NER'd again" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalidentifiers", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now))
    )
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file", doclib = docNER)

    Text.unapply(d) should be (None)
  }}

  "A  text doc which has one missing NER flag" should { "have 1 NER sendable" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now))
    )
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file", doclib = docNER)
    val (key, result) = Text.unapply(d).get
    assert(result.isInstanceOf[Sendables])
    assert(result.nonEmpty)
    assert(result.length == 1)
    assert(result.forall(s => s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.forall(s =>
      List("ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A  text doc which has all NER flags but without some end timestamp" should { "have no sendables" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities", version = consumerVersion, started = LocalDateTime.now.some, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalidentifiers", version = consumerVersion, started = LocalDateTime.now.some, ended = None)
    )
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file", doclib = docNER)

    Text.unapply(d) should be (None)
  }}
}
