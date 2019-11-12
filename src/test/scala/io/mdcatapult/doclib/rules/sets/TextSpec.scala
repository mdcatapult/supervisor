package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.{Queue, Registry}
import org.mongodb.scala.bson.ObjectId

import scala.concurrent.ExecutionContextExecutor

class TextSpec extends CommonSpec {

  implicit override val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  flags: "doclib"
      |}
      |supervisor {
      |   tabular: {
      |    totsv: {
      |      required: [{
      |        flag: "tabular.totsv"
      |        route: "doclib.tabular.totsv"
      |        type: "queue"
      |      }]
      |    }
      |    analyse {
      |      required: [{
      |        flag: "tabular.analysis"
      |        route: "doclib.tabular.analysis"
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
    """.stripMargin)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

  val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.pdf",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )


  "A doc with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Text.unapply(d)
    assert(result.isEmpty)
  }}

  "A new text doc " should { "return 3 NER sendables" in {
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file")
    val result = Text.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 3)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A new tabular doc " should { "return 3 NER sendables" in {
    val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val result = Text.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 3)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("ner.chemblactivityterms", "ner.chemicalentities", "ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A  text doc which has been NER'd" should { "not be NER'd again" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalidentifiers", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now))
    )
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file", doclib = docNER)
    val result = Text.unapply(d)
    assert(result == None)
  }}

  "A  text doc which has one missing NER flag" should { "have 1 NER sendable" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now))
    )
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file", doclib = docNER)
    val result = Text.unapply(d)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("ner.chemicalidentifiers")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A  text doc which has all NER flags but without some end timestamp" should { "have no sendables" in {
    val docNER = List(
      DoclibFlag(key = "ner.chemblactivityterms", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalentities", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now)),
      DoclibFlag(key = "ner.chemicalidentifiers", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = None)
    )
    val d = dummy.copy(mimetype = "text/plain", source = "/dummy/path/to/dummy/file", doclib = docNER)
    val result = Text.unapply(d)
    assert(result.get.isEmpty)
  }}
}
