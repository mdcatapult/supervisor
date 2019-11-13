package io.mdcatapult.doclib.consumers

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Tabular
import io.mdcatapult.klein.queue.Registry
import org.mongodb.scala.bson.ObjectId
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContextExecutor

class ConsumerSupervisorSpec extends TestKit(ActorSystem("SupervisorHandlerSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with WordSpecLike with BeforeAndAfterAll with MockFactory {

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


  "A flag which does not route to a queue type" should { "throw exception " in {
    implicit val doc = dummy.copy(mimetype = "dummy/mimetype")
    val flag = "supervisor.someprocess"
    val caught = intercept[Exception]{
      Tabular.getSendables(flag)
    }
    assert(caught.getMessage == s"Unable to handle configured type 'something' for required flag $flag")
  }}
}
