package io.mdcatapult.doclib.consumers

import java.time.temporal.ChronoUnit.MILLIS

import akka.actor._
import akka.pattern.ask
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import better.files.{File => ScalaFile}
import cats.implicits._
import com.spingo.op_rabbit.Message.ConfirmResponse
import com.spingo.op_rabbit.PlayJsonSupport._
import com.spingo.op_rabbit.{Message, SubscriptionRef}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.{ConsumerVersion, DoclibDoc, DoclibFlag, FileAttrs}
import io.mdcatapult.doclib.util.ImplicitOrdering.localDateOrdering._
import io.mdcatapult.doclib.util.{DirectoryDelete, MongoCodecs, nowUtc}
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.{Queue, Registry, Sendable}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters.{equal => Mequal}
import org.mongodb.scala.model.Updates.combine
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class ConsumerSupervisorIntegrationTest extends TestKit(ActorSystem("SupervisorIntegrationTest", ConfigFactory.parseString(
  """
akka.loggers = ["akka.testkit.TestEventListener"]
"""))) with ImplicitSender
  with AnyFlatSpecLike
  with Matchers
  with BeforeAndAfterAll with MockFactory with ScalaFutures with DirectoryDelete with Eventually {

  implicit val config: Config = ConfigFactory.load()

  implicit val timeout: Timeout = Timeout(5 seconds)

  // Queue for supervisor.flag.one
  var messagesFromQueueOne = List[(String, DoclibMsg)]()

  // Note that we need to include a topic if we want the queue to be created
  val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
  val flagOneQNAme = "supervisor.flag.one"

  val flagOneQ = Queue[DoclibMsg](flagOneQNAme, consumerName)

  val subscription: SubscriptionRef =
    flagOneQ.subscribe((msg: DoclibMsg, key: String) => messagesFromQueueOne ::= key -> msg)

  Await.result(subscription.initialized, 5.seconds)

  val flagOneQMessages: Future[ConfirmResponse] = (
    flagOneQ.rabbit ? Message.queue(
      DoclibMsg("test message"),
      queue = flagOneQNAme)
    ).mapTo[ConfirmResponse]

  whenReady(flagOneQMessages) { response =>
    response shouldBe a[Message.Ack]
  }

  // Queue for supervisor.flag.two
  var messagesFromQueueTwo = List[(String, DoclibMsg)]()

  val flagTwoQNAme = "supervisor.flag.two"

  val flagTwoQ = Queue[DoclibMsg](flagTwoQNAme, consumerName)

  val subscriptionTwo: SubscriptionRef =
    flagTwoQ.subscribe((msg: DoclibMsg, key: String) => messagesFromQueueTwo ::= key -> msg)

  Await.result(subscriptionTwo.initialized, 5.seconds)

  val flagTwoQMessages: Future[ConfirmResponse] = (
    flagTwoQ.rabbit ? Message.queue(
      DoclibMsg("test message 2"),
      queue = flagTwoQNAme)
    ).mapTo[ConfirmResponse]

  whenReady(flagTwoQMessages) { response =>
    response shouldBe a[Message.Ack]
  }

  private val timeNow = nowUtc.now().truncatedTo(MILLIS)

  def createNewDoc(source: String, mimetype: String): DoclibDoc = {
    val path = ScalaFile(source).path
    val fileAttrs = FileAttrs(
      path = path.getParent.toAbsolutePath.toString,
      name = path.getFileName.toString,
      mtime = timeNow,
      ctime = timeNow,
      atime = timeNow,
      size = 5
    )
    //TODO what should created and updated time be. Mime type? From getMimeType or from some metadata? More than one?
    DoclibDoc(
      _id = new ObjectId(),
      source = source,
      hash = "12345",
      derivative = false,
      created = timeNow,
      updated = timeNow,
      mimetype = mimetype,
      attrs = Some(fileAttrs)
    )
  }

  implicit val codecs: CodecRegistry = MongoCodecs.get
  val mongo: Mongo = new Mongo()
  implicit val collection: MongoCollection[DoclibDoc] = mongo.database.getCollection(config.getString("mongo.collection"))

  implicit val m: Materializer = Materializer(system)

  implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

  val handler = new SupervisorHandler()

  private val dummy = DoclibDoc(
    _id = new ObjectId,
    source = "dummy.txt",
    hash = "01234567890",
    derivative = false,
    created = timeNow,
    updated = timeNow,
    mimetype = "text/plain"
  )

  override def beforeAll(): Unit = {
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
        started = timeNow.some,
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
        started = timeNow.plusHours(1).some,
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
        started = timeNow.some,
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
        started = timeNow.some,
        ended = Some(timeNow)
      )
    )
    val doc = dummy.copy(mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val msg = SupervisorMsg("1234", Some(List("first.flag", "second.flag")))
    val result = Await.result(collection.insertOne(doc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))    // reset first and second flags which should also dedup first to the most current one
    Await.result(handler.reset(doc, msg), 5.seconds)
    val updatedDoc = Await.result(collection.find(Mequal("_id", doc._id)).toFuture(), 5.seconds)
    val flag = updatedDoc.head.getFlag("first.flag").head
    flag.reset should not be None

    assert(flag.reset.get.truncatedTo(MILLIS) >= timeNow)
    assert(flag.started.get.truncatedTo(MILLIS) >= timeNow.plusHours(1))
    assert(flag.ended.get.truncatedTo(MILLIS) >= timeNow.plusHours(1).plusMinutes(1))
    val secondFlag = updatedDoc.head.getFlag("second.flag").head
    secondFlag.reset should not be None
    assert(secondFlag.reset.get.truncatedTo(MILLIS) >= timeNow)
  }

  "A supervisor message without reset" should "not reset the flags" in {
    val flags = List(
      DoclibFlag(
        key = "first.flag",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = timeNow.some,
        ended = Some(timeNow)
      ),
      DoclibFlag(
        key = "second.flag",
        version = ConsumerVersion(
          number = "0.0.1",
          major = 0,
          minor = 0,
          patch = 1,
          hash = "1234567890"),
        started = timeNow.some,
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
        started = timeNow.some,
        ended = Some(timeNow)
      )
    )

    val doc = dummy.copy(_id = new ObjectId, mimetype = "application/pdf", source = "/dummy/path/to/dummy/file", doclib = flags)
    val msg = SupervisorMsg("1234")

    val result = Await.result(collection.insertOne(doc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))
    // reset first and second flags which should also dedup first to the most current one
    Await.result(handler.reset(doc, msg), 5.seconds)

    val updatedDoc = Await.result(collection.find(Mequal("_id", doc._id)).toFuture(), 5.seconds)

    val flag = updatedDoc.head.getFlag("first.flag").head
    flag.reset should be (None)
    assert(flag.started.get.truncatedTo(MILLIS) == timeNow)
    assert(flag.ended.get.truncatedTo(MILLIS) == timeNow)

    val secondFlag = updatedDoc.head.getFlag("second.flag").head
    secondFlag.reset should be (None)
  }

  "A flag which is not queued" can "be queued" in {
//    val flag = new DoclibFlag(key = "tabular.totsv", version = ConsumerVersion(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = false)
    val flagDoc = dummy.copy(_id = new ObjectId)
    val result = Await.result(collection.insertOne(flagDoc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))
    val sendable: Sendable[DoclibMsg] = flagOneQ
    handler.publish(flagDoc, List[Sendable[DoclibMsg]](sendable), "flag-test")
    Await.result(handler.updateQueueStatus(flagDoc, List[Sendable[DoclibMsg]](sendable), "flag-test"), 5.seconds)
    val docResult = Await.result(collection.find(Mequal("_id", flagDoc._id)).toFuture(), 5.seconds)
    assert(docResult.head.getFlag("supervisor.flag.one").head.queued)
    assert(docResult.head.doclib.length == 1)
    eventually {
      messagesFromQueueOne should contain ("" -> DoclibMsg(flagDoc._id.toHexString))
    }
  }

  "Multiple unqueued flags" should "be queued" in {
    val flag = new DoclibFlag(key = "supervisor.flag.one", version = ConsumerVersion(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = true)
    val flagTwo = new DoclibFlag(key = "supervisor.flag.two", version = ConsumerVersion(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = false)
    val flagDoc = dummy.copy(_id = new ObjectId, doclib = List(flag, flagTwo))
    val result = Await.result(collection.insertOne(flagDoc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))
    val sendableOne: Sendable[DoclibMsg] = flagOneQ
    val sendableTwo: Sendable[DoclibMsg] = flagTwoQ
    handler.publish(flagDoc, List[Sendable[DoclibMsg]](sendableOne, sendableTwo), "flag-test")
    Await.result(handler.updateQueueStatus(flagDoc, List[Sendable[DoclibMsg]](sendableOne, sendableTwo), "flag-test"), 5.seconds)
    val docResult = Await.result(collection.find(Mequal("_id", flagDoc._id)).toFuture(), 5.seconds)
    assert(docResult.head.getFlag("supervisor.flag.one").head.queued)
    assert(docResult.head.getFlag("supervisor.flag.two").head.queued)
    assert(docResult.head.doclib.length == 2)
    eventually {
      messagesFromQueueOne should not contain ("" -> DoclibMsg(flagDoc._id.toHexString))
      messagesFromQueueTwo should contain ("" -> DoclibMsg(flagDoc._id.toHexString))
    }
  }

}
