/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mdcatapult.doclib.consumers

import java.time.temporal.ChronoUnit.MILLIS
import org.apache.pekko.actor._
import org.apache.pekko.stream.Materializer
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.apache.pekko.util.Timeout
import better.files.{File => ScalaFile}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.codec.MongoCodecs
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.{AppConfig, DoclibDoc, DoclibFlag, FileAttrs}
import io.mdcatapult.util.time.ImplicitOrdering.localDateOrdering._
import io.mdcatapult.util.time.nowUtc
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.{Envelope, Queue, Sendable}
import io.mdcatapult.util.concurrency.SemaphoreLimitedExecution
import io.mdcatapult.util.models.Version
import org.apache.pekko.stream.connectors.amqp.scaladsl.CommittableReadResult
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters.{equal => Mequal}
import org.mongodb.scala.model.Updates.combine
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.{Format, Json}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Success, Try}

object Message {
  implicit val msgFormatter: Format[Message] = Json.format[Message]
}
case class Message(message: String) extends Envelope {
  override def toJsonString(): String = {
    Json.toJson(this).toString()
  }
}
class ConsumerSupervisorIntegrationTest extends TestKit(ActorSystem("SupervisorIntegrationTest", ConfigFactory.parseString(
  """
akka.loggers = ["akka.testkit.TestEventListener"]
"""))) with ImplicitSender
  with AnyFlatSpecLike
  with Matchers
  with BeforeAndAfterAll with MockFactory with ScalaFutures with Eventually with BeforeAndAfterEach {

  implicit val config: Config = ConfigFactory.load()

  private val readLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.read-limit"))
  private val writeLimiter = SemaphoreLimitedExecution.create(config.getInt("mongo.write-limit"))

  implicit val appConfig: AppConfig =
    AppConfig(
      config.getString("consumer.name"),
      config.getInt("consumer.concurrency"),
      config.getString("consumer.queue"),
      Option(config.getString("consumer.exchange"))
    )

  implicit val m: Materializer = Materializer(system)
  implicit val timeout: Timeout = Timeout(5 seconds)

  private val messagesFromQueueOne = new ListBuffer[DoclibMsg]()

  // Note that we need to include a topic if we want the queue to be created
  private val consumerName = Option(config.getString("consumer.name"))

  val queueOneBusinessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
    val msg = Json.parse(committableReadResult.message.bytes.utf8String).as[DoclibMsg]
    messagesFromQueueOne += msg
    Future((committableReadResult, Success(Message("Yippee"))))
  }
  private val messagesFromQueueTwo = ListBuffer[DoclibMsg]()
  val queueTwoBusinessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
    val msg = Json.parse(committableReadResult.message.bytes.utf8String).as[DoclibMsg]
    messagesFromQueueTwo += msg
    Future((committableReadResult, Success(Message("Yippee"))))
  }
  private val messagesFromQueueThree = ListBuffer[DoclibMsg]()
  val queueThreeBusinessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
    val msg = Json.parse(committableReadResult.message.bytes.utf8String).as[DoclibMsg]
    messagesFromQueueThree += msg
    Future((committableReadResult, Success(Message("Yippee"))))
  }

  // Queue for supervisor.flag.one

  private val flagOneQNAme = "supervisor.flag.one"

  private val flagOneQ = Queue[DoclibMsg, Message](name = flagOneQNAme, consumerName = consumerName)
  flagOneQ.subscribe(queueOneBusinessLogic)

  // Queue for supervisor.flag.two

  private val flagTwoQNAme = "supervisor.flag.two"

  private val flagTwoQ = Queue[DoclibMsg, Message](name = flagTwoQNAme, consumerName = consumerName)

  flagTwoQ.subscribe(queueTwoBusinessLogic)

  // Queue for supervisor.flag.three

  private val flagThreeQNAme = "supervisor.flag.three"

  private val flagThreeQ = Queue[DoclibMsg, Message](name = flagThreeQNAme, consumerName = consumerName)

  flagThreeQ.subscribe(queueThreeBusinessLogic)

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
  implicit val collection: MongoCollection[DoclibDoc] = mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.documents-collection"))



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

    val handler = SupervisorHandler(readLimiter, writeLimiter)

    val flags = List(
      DoclibFlag(
        key = "first.flag",
        version = Version(
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
        version = Version(
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
        version = Version(
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
        version = Version(
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
    assert(result.exists(_.wasAcknowledged())) // reset first and second flags which should also dedup first to the most current one
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
    val handler = SupervisorHandler(readLimiter, writeLimiter)

    val flags = List(
      DoclibFlag(
        key = "first.flag",
        version = Version(
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
        version = Version(
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
        version = Version(
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
    flag.reset should be(None)
    assert(flag.started.get.truncatedTo(MILLIS) == timeNow)
    assert(flag.ended.get.truncatedTo(MILLIS) == timeNow)

    val secondFlag = updatedDoc.head.getFlag("second.flag").head
    secondFlag.reset should be(None)
  }

  "A flag which is not queued" can "be queued" in {
    val flagDoc = dummy.copy(_id = new ObjectId())
    val result = Await.result(collection.insertOne(flagDoc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))

    val sendable: Sendable[DoclibMsg] = flagOneQ
    val msg = new SupervisorMsg(id = "1234")

    val handler = new SupervisorHandler(
      engine = { (doc: DoclibDoc) =>
        if (doc == flagDoc)
          Option("supervisor.flag-test" -> List(sendable))
        else
          None
      },
      readLimiter,
      writeLimiter
    )

    Await.result(handler.sendMessages(flagDoc, msg), 5.seconds)

    val docResult = Await.result(collection.find(Mequal("_id", flagDoc._id)).toFuture(), 5.seconds)
    assert(docResult.head.getFlag("supervisor.flag.one").head.isQueued)
    assert(docResult.head.doclib.length == 1)
    eventually(timeout(Span(5, Seconds))) {
      messagesFromQueueOne should contain(DoclibMsg(flagDoc._id.toHexString))
    }
  }

  "Unqueued flags" should "be queued" in {
    val flag = new DoclibFlag(key = "supervisor.flag.one", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(true))
    val flagTwo = new DoclibFlag(key = "supervisor.flag.two", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(false))
    val flagDoc = dummy.copy(_id = new ObjectId, doclib = List(flag, flagTwo))
    println(s"ID for flag doc is ${flagDoc._id.toHexString}")

    val result = Await.result(collection.insertOne(flagDoc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))

    val sendableOne: Sendable[DoclibMsg] = flagOneQ
    val sendableTwo: Sendable[DoclibMsg] = flagTwoQ
    val msg = new SupervisorMsg(id = "1234")


    val handler = new SupervisorHandler(
      engine = { (doc: DoclibDoc) =>
        if (doc == flagDoc)
          Option("supervisor.flag-test" -> List(sendableOne, sendableTwo))
        else
          None
      },
      readLimiter,
      writeLimiter
    )

    Await.result(handler.sendMessages(flagDoc, msg), 5.seconds)

    val docResult = Await.result(collection.find(Mequal("_id", flagDoc._id)).toFuture(), Duration.Inf)

    assert(docResult.head.getFlag("supervisor.flag.one").head.isQueued)
    assert(docResult.head.getFlag("supervisor.flag.two").head.isQueued)
    assert(docResult.head.doclib.length == 2)
    eventually(timeout(Span(5, Seconds))) {
      messagesFromQueueOne should not contain DoclibMsg(flagDoc._id.toHexString)
      messagesFromQueueTwo should contain(DoclibMsg(flagDoc._id.toHexString))
    }

  }

  "Multiple unqueued flags" should "be queued" in {
    val flag = new DoclibFlag(key = "supervisor.flag.one", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(true))
    val flagTwo = new DoclibFlag(key = "supervisor.flag.two", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(false))
    val flagDoc = dummy.copy(_id = new ObjectId, doclib = List(flag, flagTwo))

    val result = Await.result(collection.insertOne(flagDoc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))

    val sendableOne: Sendable[DoclibMsg] = flagOneQ
    val sendableTwo: Sendable[DoclibMsg] = flagTwoQ
    val sendableThree: Sendable[DoclibMsg] = flagThreeQ
    val sendables: List[Sendable[DoclibMsg]] = List(
      sendableOne,
      sendableTwo,
      sendableThree
    )
    val msg = new SupervisorMsg(id = "1234")

    val handler = new SupervisorHandler(
      engine = {
        (doc: DoclibDoc) =>
          if (doc == flagDoc)
            Option("supervisor.flag-test" -> sendables)
          else
            None
      },
      readLimiter,
      writeLimiter
    )


    Await.result(handler.sendMessages(flagDoc, msg), 5.seconds)

    val docResult = Await.result(collection.find(Mequal("_id", flagDoc._id)).toFuture(), 5.seconds)

    assert(docResult.head.getFlag("supervisor.flag.one").head.isQueued)
    assert(docResult.head.getFlag("supervisor.flag.two").head.isQueued)
    assert(docResult.head.getFlag("supervisor.flag.three").head.isQueued)
    assert(docResult.head.doclib.length == 3)

    eventually(timeout(Span(5, Seconds))) {
      messagesFromQueueTwo should contain(DoclibMsg(flagDoc._id.toHexString))
      messagesFromQueueThree should contain(DoclibMsg(flagDoc._id.toHexString))
    }
  }

  "A flag which has been queued already but is reset" can "be queued" in {
    val flag = new DoclibFlag(key = "supervisor.flag.one", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(true))
    val flagTwo = new DoclibFlag(key = "supervisor.flag.two", version = Version(number = "123", major = 1, minor = 1, patch = 1, hash = "abc"), queued = Some(false))
    val flagDoc = dummy.copy(_id = new ObjectId, doclib = List(flag, flagTwo))

    val result = Await.result(collection.insertOne(flagDoc).toFutureOption(), 5.seconds)
    assert(result.exists(_.wasAcknowledged()))

    val sendableOne: Sendable[DoclibMsg] = flagOneQ
    val sendableTwo: Sendable[DoclibMsg] = flagTwoQ
    val sendableThree: Sendable[DoclibMsg] = flagThreeQ
    val sendables: List[Sendable[DoclibMsg]] = List(
      sendableOne,
      sendableTwo,
      sendableThree
    )

    val msg = new SupervisorMsg(id = "1234", reset = Some(List("supervisor.flag.one")))

    val handler = new SupervisorHandler(
      engine = { (doc: DoclibDoc) =>
        if (doc == flagDoc)
          Option("supervisor.flag-test" -> sendables)
        else
          None
      },
      readLimiter,
      writeLimiter
    )

    Await.result(handler.sendMessages(flagDoc, msg), 5.seconds)

    val docResult = Await.result(collection.find(Mequal("_id", flagDoc._id)).toFuture(), 5.seconds)

    assert(docResult.head.getFlag("supervisor.flag.one").head.isQueued)
    assert(docResult.head.getFlag("supervisor.flag.two").head.isQueued)
    assert(docResult.head.getFlag("supervisor.flag.three").head.isQueued)
    assert(docResult.head.doclib.length == 3)

    eventually(timeout(Span(5, Seconds))) {
      messagesFromQueueOne should contain(DoclibMsg(flagDoc._id.toHexString))
      messagesFromQueueTwo should contain(DoclibMsg(flagDoc._id.toHexString))
      messagesFromQueueThree should contain(DoclibMsg(flagDoc._id.toHexString))
    }
  }

}