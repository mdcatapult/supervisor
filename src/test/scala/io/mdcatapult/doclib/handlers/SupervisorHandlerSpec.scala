package io.mdcatapult.doclib.handlers

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.TestKit
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.doclib.codec.MongoCodecs
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Registry
import io.mdcatapult.util.models.Version
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class SupervisorHandlerSpec extends TestKit(ActorSystem("SupervisorHandlerSpec", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with AnyFlatSpecLike with BeforeAndAfterAll with MockFactory {

  implicit val config: Config = ConfigFactory.parseString(
    """
      |doclib {
      |  root: "./test"
      |  remote {
      |    target-dir: "remote"
      |    temp-dir: "remote-ingress"
      |  }
      |  local {
      |    target-dir: "local"
      |    temp-dir: "ingress"
      |  }
      |  archive {
      |    target-dir: "archive"
      |  }
      |}
      |version {
      |  number = "supervisor-test",
      |  major = 1,
      |  minor =  2,
      |  patch = 3,
      |  hash =  "12345"
      |}
      |op-rabbit {
      |  topic-exchange-name = supervisor-test
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
      |    virtual-host = "supervisor-test"
      |    hosts = ["localhost"]
      |    username = "doclib"
      |    password = "doclib"
      |    port = 5672
      |    ssl = false
      |    connection-timeout = 3s
      |  }
      |}
      |mongo {
      |  database: "supervisor-test"
      |  collection: "documents"
      |  connection {
      |    username: "doclib"
      |    password: "doclib"
      |    database: "admin"
      |    hosts: ["localhost"]
      |  }
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
  implicit val m: Materializer = Materializer(system)
  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()
  implicit val codecs: CodecRegistry = MongoCodecs.get
  val mongo: Mongo = new Mongo()

  val wrappedCollection: JMongoCollection[DoclibDoc] = stub[JMongoCollection[DoclibDoc]]
  implicit val collection: MongoCollection[DoclibDoc] = MongoCollection[DoclibDoc](wrappedCollection)

  private val handler = SupervisorHandler()

  implicit val doc: DoclibDoc = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.txt",
    hash = "01234567890",
    derivative = false,
    created = LocalDateTime.now(),
    updated = LocalDateTime.now(),
    mimetype = "text/plain"
  )

  "A flag which has not been queued already" can "be queued " in {
    val flag = new DoclibFlag(key = "tabular.totsv", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"))
    val flagConfig: Config = config.getConfigList("supervisor.tabular.totsv.required").asScala.head
    val flagDoc = doc.copy(doclib = List(flag))
    val msg = SupervisorMsg(id = "1234")
    assert(handler.canQueue(flagDoc, flagConfig, msg))
  }

  "A flag which has been queued already" can "not be queued " in {
    val flag = new DoclibFlag(key = "tabular.totsv", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(true))
    val flagConfig: Config = config.getConfigList("supervisor.tabular.totsv.required").asScala.head
    val flagDoc = doc.copy(doclib = List(flag))
    val msg = SupervisorMsg(id = "1234")
    assert(!handler.canQueue(flagDoc, flagConfig, msg))
  }

  "A flag which is already queued but has been reset" can "be queued " in {
    val flag = new DoclibFlag(key = "tabular.totsv", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(true))
    val flagConfig: Config = config.getConfigList("supervisor.tabular.totsv.required").asScala.head
    val flagDoc = doc.copy(doclib = List(flag))
    val msg = SupervisorMsg(id = "1234", Some(List("tabular.totsv")))
    assert(handler.canQueue(flagDoc, flagConfig, msg))
  }

}
