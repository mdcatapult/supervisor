package io.mdcatapult.doclib.consumers

import java.time.LocalDateTime
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Tabular
import org.mongodb.scala.bson.ObjectId
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global

class ConsumerSupervisorSpec extends TestKit(ActorSystem("ConsumerSupervisorSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with AnyWordSpecLike with BeforeAndAfterAll with MockFactory {

  implicit val config: Config = ConfigFactory.parseString(
    """
      |op-rabbit {
      |  topic-exchange-name = "doclib"
      |}
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
      |  someprocess: {
      |    required: [{
      |      flag: "someprocess"
      |      route: "someprocess"
      |      type: "something"
      |    }]
      |  }
      |}
      |analytical {
      |  name: "analytical.supervisor"
      |}
    """.stripMargin)
  implicit val m: Materializer = Materializer(system)

  private val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )


  "A flag which does not route to a queue type" should { "throw exception " in {
    implicit val doc: DoclibDoc = dummy.copy(mimetype = "dummy/mimetype")
    val flag = "supervisor.someprocess"
    val caught = intercept[Exception]{
      Tabular.getSendables(flag)
    }
    assert(caught.getMessage == s"Unable to handle configured type 'something' for required flag $flag")
  }}
}
