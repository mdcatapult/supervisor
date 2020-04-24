package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class CommonSpec extends TestKit(ActorSystem("SupervisorHandlerSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with AnyWordSpecLike with BeforeAndAfterAll with MockFactory {
  implicit val config: Config = ConfigFactory.parseString("")
//    """
//      |supervisor {
//      |  archive: {
//      |    required: [{
//      |      flag: "unarchived"
//      |      route: "doclib.unarchive"
//      |      type: "queue"
//      |    }]
//      |  }
//      |  tabular: {
//      |    convertToTsv: {
//      |      required: [{
//      |        flag: "totsv"
//      |        route: "doclib.totsv"
//      |        type: "queue"
//      |      }]
//      |    }
//      |    tableProcessing {
//      |      required: [{
//      |        flag: "table_process"
//      |        route: "doclib.table_process"
//      |        type: "queue"
//      |      }]
//      |    }
//      |  }
//      |  html: {
//      |    required: [{
//      |      flag: "html.screenshot"
//      |      route: "doclib.html.screenshot"
//      |      type: "queue"
//      |    },{
//      |      flag: "ner.render"
//      |      route: "doclib.html.render"
//      |      type: "queue"
//      |    }]
//      |  }
//      |  xml: {
//      |    required: []
//      |  }
//      |  text: {
//      |    required: []
//      |  }
//      |  document: {
//      |    required: []
//      |  }
//      |  ner: {
//      |    required: [{
//      |      flag: "ner.chemblactivityterms"
//      |      route: "doclib.ner.chemblactivityterms"
//      |      type: "queue"
//      |    },{
//      |      flag: "ner.chemicalentities"
//      |      route: "doclib.ner.chemicalentities"
//      |      type: "queue"
//      |    },{
//      |      flag: "ner.chemicalidentifiers"
//      |      route: "doclib.ner.chemicalidentifiers"
//      |      type: "queue"
//      |    }]
//      |  }
//      |}
//    """.stripMargin)
//  implicit val system: ActorSystem = ActorSystem("scalatest", config)
//  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global


//  def baselineTests(r: Rule, flag: String, props: Seq[(String, BsonValue)] = List[(String, BsonValue)]()): Unit = {
//    "An empty (invalid) Document" should " return None" in {
//      assert(r.unapply(MongoDoc()).isEmpty)
//    }
//
//    "An un-started Document" should "return some Sendables " in {
//      val d = MongoDoc(List("doclib" -> BsonArray(flag -> BsonNull())) ++ props)
//      val result = r.unapply(d)
//      assert(result.isDefined)
//      assert(result.get.isInstanceOf[Sendables])
//      assert(result.get.isEmpty)
//    }
//
//    "A valid Document with a TRUE flag " should " return None" in {
//      val d = MongoDoc(List("doclib" -> BsonDocument(flag -> BsonBoolean(true))) ++ props)
//      val result = r.unapply(d)
//      assert(result.isEmpty)
//    }
//
//    "A valid Document with a FALSE flag " should "None" in {
//      val d = MongoDoc(List("doclib" -> BsonDocument(flag -> BsonBoolean(false))) ++ props)
//      val result = r.unapply(d)
//      assert(result.isEmpty)
//    }
//
//    "A valid Document with no flag" should " return Some(Sendable) with at least one exchange" in {
//      val d = MongoDoc() ++ props
//      val result = r.unapply(d)
//      assert(result.nonEmpty)
//      assert(result.get.nonEmpty)
//      assert(result.get.head.isInstanceOf[Exchange[DoclibMsg]])
//    }
//  }
}
