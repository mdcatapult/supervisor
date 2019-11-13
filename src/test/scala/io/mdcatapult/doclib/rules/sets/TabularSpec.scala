package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.klein.queue.{Queue, Registry}
import org.mongodb.scala.bson.ObjectId

import scala.concurrent.ExecutionContextExecutor

class TabularSpec extends CommonSpec {

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
    source = "dummt.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )


  "A Tabular doc with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Tabular.unapply(d)
    assert(result.isEmpty)
  }}

  "An un-started Tabular TSV doc" should { "return a tsv sendable" in {
    implicit val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val result = Tabular.getSendables("supervisor.tabular.totsv")
    assert(result.length == 1)
    assert(result.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.forall(s ⇒
      List("tabular.totsv")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

  "An Tabular doc with partially completed extraction" should { "return empty sendables" in {
    val d = dummy.copy(
      mimetype = "text/csv",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = 2.0,
          hash = "dev",
          started = LocalDateTime.now,
          ended = None
        )
      )
    )
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }}

  "An extracted Tabular doc with partially completed analysis" should { "not require analysis" in {
    implicit val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = 2.0,
          hash = "dev",
          started = LocalDateTime.now,
          ended = Some(LocalDateTime.now())),
      DoclibFlag(
        key = "ner.chemblactivityterms",
        version = 2.0,
        hash = "dev",
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now)),
      DoclibFlag(
        key = "ner.chemicalentities",
        version = 2.0,
        hash = "dev",
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now)),
      DoclibFlag(
        key = "ner.chemicalidentifiers",
        version = 2.0, hash = "dev",
        started = LocalDateTime.now,
        ended = Some(LocalDateTime.now)),
        DoclibFlag(
          key = "tabular.analysis",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now())
      ))
    val result = Tabular.requiredAnalysis()
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }}

  "A complete TSV doc" should { "return None" in {
    val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.totsv",
          version = 2.0,
          hash = "dev",
          started = LocalDateTime.now,
          ended = Some(LocalDateTime.now)),
        DoclibFlag(
          key = "tabular.analysis",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now(),
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
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
      ))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].name == "tabular.totsv")
  }}

  "A complete Tabular doc" should { "return None" in {
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "tabular.analysis",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "tabular.totsv",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())
        )))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }}

  "A tabular doc which has not been NER'd " should { "return 3 NER sendables" in {
    val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val result = Tabular.unapply(d)
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
    val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file", doclib = docNER)
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("tabular.analysis")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].name)))
  }}

}
