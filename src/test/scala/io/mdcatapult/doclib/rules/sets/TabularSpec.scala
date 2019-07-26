package io.mdcatapult.doclib.rules.sets

import java.util.Date

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.klein.queue.Queue
import org.mongodb.scala.bson.{BsonArray, BsonDateTime, BsonDocument, BsonDouble, BsonNull, BsonString}
import org.mongodb.scala.{Document ⇒ MongoDoc}

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
  implicit val system: ActorSystem = ActorSystem("scalatest", config)
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  "An Tabular doc with no mimetype" should "return None " in {
    val d = MongoDoc(List("doclib" → BsonArray()))
    val result = Tabular.unapply(d)
    assert(result.isEmpty)
  }

  "An Tabular doc with an unmatched mimetype" should "return None " in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("dummy/mimetype"),
      "doclib" → BsonArray()))
    val result = Tabular.unapply(d)
    assert(result.isEmpty)
  }

  "An un-started Tabular TSV doc with no NER" should "return 2 NER sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("text/tab-separated-values"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray()))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 2)
    assert(result.get.forall(s ⇒ s.isInstanceOf[Queue[DoclibMsg]]))
    assert(result.get.forall(s ⇒
      List("doclib.ner.chemblactivityterms", "doclib.ner.chemicalentities")
        .contains(s.asInstanceOf[Queue[DoclibMsg]].queueName)))
  }

  "An un-started Tabular doc with partially completed NER" should "return empty sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("text/tab-separated-values"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonNull())
      ))))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }

  "An un-started Tabular doc with partially missing NER" should "return 1 NER sendable" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("text/tab-separated-values"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date()))
      ))))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.ner.chemicalentities")
  }

  "An NER complete TSV doc that has not been started" should "return 1 table process sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("text/tab-separated-values"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date()))
      ))))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.tabular.analysis")
  }

  "An NER complete TSV doc that has been started but not completed" should "return empty sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("text/tab-separated-values"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("tabular.analysis"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonNull())
      ))))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }

  "A complete TSV doc" should "return None" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("text/tab-separated-values"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("tabular.analysis"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date()))
      ))))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }


  "An NER complete Tabular doc that has not been started" should "return 1 totsv sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("application/vnd.ms-excel"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date()))
      ))))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.nonEmpty)
    assert(result.get.length == 1)
    assert(result.get.head.isInstanceOf[Queue[DoclibMsg]])
    assert(result.get.head.asInstanceOf[Queue[DoclibMsg]].queueName == "doclib.tabular.totsv")
  }

  "An NER complete Tabular doc that has been started but not completed" should "return empty sendables" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("application/vnd.ms-excel"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("tabular.totsv"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonNull())
      ))))
    val result = Tabular.unapply(d)
    assert(result.isDefined)
    assert(result.get.isInstanceOf[Sendables])
    assert(result.get.isEmpty)
  }

  "A complete Tabular doc" should "return None" in {
    val d = MongoDoc(List(
      "mimetype" → BsonString("application/vnd.ms-excel"),
      "source" → BsonString("/dummy/path/to/dummy/file"),
      "doclib" → BsonArray(List(
        BsonDocument(
          "key" → BsonString("ner.chemblactivityterms"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("ner.chemicalentities"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date())),
        BsonDocument(
          "key" → BsonString("tabular.totsv"),
          "version" → BsonDouble(2.0),
          "hash" → BsonString("123456"),
          "started" → BsonDateTime(new Date()),
          "ended" → BsonDateTime(new Date()))
      ))))
    val result = Archive.unapply(d)
    assert(result.isEmpty)
  }

}
