package io.mdcatapult.doclib.consumers

import java.time.{LocalDateTime, ZoneOffset}

import akka.actor._
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import better.files.{File => ScalaFile}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.SupervisorMsg
import io.mdcatapult.doclib.models.{ConsumerVersion, DoclibDoc, DoclibFlag, FileAttrs}
import io.mdcatapult.doclib.util.{DirectoryDelete, MongoCodecs}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.Sendable
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters.{equal => Mequal}
import org.mongodb.scala.model.Updates.combine
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class ConsumerSupervisorIntegrationTest extends TestKit(ActorSystem("SupervisorIntegrationTest", ConfigFactory.parseString(
  """
akka.loggers = ["akka.testkit.TestEventListener"]
"""))) with ImplicitSender
  with AnyFlatSpecLike
  with Matchers
  with BeforeAndAfterAll with MockFactory with ScalaFutures with DirectoryDelete {

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
      |  archive: {
      |    required: [{
      |      flag: "unarchived"
      |      route: "doclib.unarchive"
      |      type: "queue"
      |    }]
      |  }
      |  tabular: {
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
      |  html: {
      |    required: [{
      |      flag: "html.screenshot"
      |      route: "doclib.html.screenshot"
      |      type: "queue"
      |    },{
      |      flag: "html.render"
      |      route: "doclib.html.render"
      |      type: "queue"
      |    }]
      |  }
      |  xml: {
      |    required: []
      |  }
      |  text: {
      |    required: []
      |  }
      |  document: {
      |    required: []
      |  }
      |  chemical: {
      |    required: []
      |  }
      |  image: {
      |    required: []
      |  }
      |  audio: {
      |    required: []
      |  }
      |  video: {
      |    required: []
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
      |    },{
      |      flag: "ner.chemicalidentifiers"
      |      route: "doclib.ner.chemicalidentifiers"
      |      type: "queue"
      |    }]
      |  }
      |}
  """.stripMargin)

  val timeNow: LocalDateTime = LocalDateTime.now

  def createNewDoc(source: String, mimetype: String): DoclibDoc = {
    val createdTime = timeNow.toInstant(ZoneOffset.UTC)
    val path = ScalaFile(source).path
    val fileAttrs = FileAttrs(
      path = path.getParent.toAbsolutePath.toString,
      name = path.getFileName.toString,
      mtime = LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC),
      ctime = LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC),
      atime = LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC),
      size = 5
    )
    //TODO what should created and updated time be. Mime type? From getMimeType or from some metadata? More than one?
    DoclibDoc(
      _id = new ObjectId(),
      source = source,
      hash = "12345",
      derivative = false,
      created = LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC),
      updated = LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC),
      mimetype = mimetype,
      attrs = Some(fileAttrs)
    )
  }

  implicit val codecs: CodecRegistry = MongoCodecs.get
  val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[DoclibDoc] = mongo.database.getCollection(config.getString("mongo.collection"))

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executor: ExecutionContext = scala.concurrent.ExecutionContext.global

  implicit val upstream: Sendable[SupervisorMsg] = stub[Sendable[SupervisorMsg]]

  val handler = new SupervisorHandler(upstream)

  val dummy = DoclibDoc(
    _id = new ObjectId(),
    source = "dummy.txt",
    hash = "01234567890",
    derivative = false,
    created = timeNow,
    updated = timeNow,
    mimetype = "text/plain"
  )

  override def beforeAll {
    Await.result(collection.deleteMany(combine()).toFuture(), Duration.Inf) // empty collection
  }

  "A supervisor message with reset" should "update the flags reset timestamp" in {
    val flags = List(
      DoclibFlag(
        key = "first.flag",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = timeNow,
        ended = Some(timeNow)
      ),
      DoclibFlag(
        key = "first.flag",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "56789"),
        started = timeNow.plusHours(1),
        ended = Some(timeNow.plusHours(1).plusMinutes(1))
      ),
      DoclibFlag(
        key = "second.flag",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = timeNow,
        ended = Some(timeNow)
      ),
      DoclibFlag(
        key = "third.flag",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = timeNow,
        ended = Some(timeNow)
      )
    )
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val msg = SupervisorMsg("1234", Some(List("first.flag", "second.flag")))
    val result = Await.result(collection.insertOne(doc).toFutureOption(), 5.seconds)
    assert(result.get.toString == "The operation completed successfully")
    // reset first and second flags which should also dedup first to the most current one
    Await.result(handler.reset(doc, msg), 5.seconds)
    val updatedDoc = Await.result(collection.find(Mequal("_id", doc._id)).toFuture(), 5.seconds)
    val flag = updatedDoc.head.getFlag("first.flag").head
    assert(flag.reset != None)
    assert(flag.reset.get.toEpochSecond(ZoneOffset.UTC) >= timeNow.toEpochSecond(ZoneOffset.UTC))
    assert(flag.started.toEpochSecond(ZoneOffset.UTC) >= timeNow.plusHours(1).toEpochSecond(ZoneOffset.UTC))
    assert(flag.ended.get.toEpochSecond(ZoneOffset.UTC) >= timeNow.plusHours(1).plusMinutes(1).toEpochSecond(ZoneOffset.UTC))
    val secondFlag = updatedDoc.head.getFlag("second.flag").head
    assert(secondFlag.reset != None)
    assert(secondFlag.reset.get.toEpochSecond(ZoneOffset.UTC) >= timeNow.toEpochSecond(ZoneOffset.UTC))
  }

}
