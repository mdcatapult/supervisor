package io.mdcatapult.doclib.rules.sets

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag, FileAttrs}
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala.bson.ObjectId
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContextExecutor

class TabularSpec extends CommonSpec {

  implicit override val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  flags: "doclib"
      |}
      |supervisor {
      |    tabular: {
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
      |  ner: {
      |    required: [{
      |      flag: "ner.chemblactivityterms"
      |      route: "doclib.ner.chemblactivityterms"
      |      type: "queue"
      |    },{
      |      flag: "ner.chemicalentities"
      |      route: "doclib.ner.chemicalentities"
      |      type: "queue"
      |    }]
      |  }
      |}
    """.stripMargin)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummt.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )


  "An Tabular doc with an unmatched mimetype" should { "return None " in {
    val d = dummy.copy(mimetype = "dummy/mimetype")
    val result = Tabular.unapply(d)
    assert(result.isEmpty)
  }}

  "An un-started Tabular TSV doc with no NER" should { "return 2 NER sendables" in {
    val d = dummy.copy(mimetype = "text/tab-separated-values", source = "/dummy/path/to/dummy/file")
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 2)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("doclib.ner.chemblactivityterms", "doclib.ner.chemicalentities")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].queueName)))
  }}

  "An un-started Tabular doc with partially completed NER" should { "return empty sendables" in {
    val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now()),
      ))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }}

  "An un-started Tabular doc with partially missing NER" should { "return 1 NER sendable" in {
    val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now()))
      ))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.ner.chemicalentities")
  }}

  "An NER complete TSV doc that has not been started" should { "return 1 table process sendables" in {
    val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
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
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.tabular.analysis")
  }}

  "An NER complete TSV doc that has been started but not completed" should { "return empty sendables" in {
    val d = dummy.copy(
      mimetype = "text/tab-separated-values",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "tabular.analysis",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now()),
      ))

    val result = Tabular.unapply(d)
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
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
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


  "An NER complete Tabular doc that has not been started" should { "return 1 totsv sendables" in {
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
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
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.tabular.totsv")
  }}

  "An NER complete Tabular doc that has been started but not completed" should { "return empty sendables" in {
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "tabular.totsv",
          version = 2.0,
          hash ="01234567890",
          started = LocalDateTime.now()
      )))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }}

  "A complete Tabular doc" should { "return None" in {
    val d = dummy.copy(
      mimetype = "application/vnd.ms-excel",
      source = "/dummy/path/to/dummy/file",
      doclib = List(
        DoclibFlag(
          key = "ner.chemblactivityterms",
          version = 2.0,
          hash = "01234567890",
          started = LocalDateTime.now(),
          ended = Some(LocalDateTime.now())),
        DoclibFlag(
          key = "ner.chemicalentities",
          version = 2.0,
          hash ="01234567890",
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

}
