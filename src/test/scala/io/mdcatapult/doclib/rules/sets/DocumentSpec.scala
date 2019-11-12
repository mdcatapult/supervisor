package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.{Queue, Registry}
import org.mongodb.scala.bson.ObjectId

import scala.concurrent.ExecutionContextExecutor

class DocumentSpec extends CommonSpec {

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


  "A Tabular doc with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Document.unapply(d)
    assert(result.isEmpty)
  }}

  "A  PDF doc which has not been converted to raw text" should { "return 1 rawtext sendable" in {
    val d = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file")
    val result = Document.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("rawtext")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "A  PDF doc which has been converted to raw text" should { "not be converted again" in {
    val docRaw = DoclibFlag(key = "rawtext", version = 2.0, hash = "dev", started = LocalDateTime.now, ended = Some(LocalDateTime.now))
    val d = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = List(docRaw))
    val result = Document.unapply(d)
    assert(result == None)
  }}

}
