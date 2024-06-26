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

package io.mdcatapult.doclib.handlers

import java.time.LocalDateTime
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.testkit.TestKit
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.{AppConfig, DoclibDoc, DoclibFlag}
import io.mdcatapult.doclib.codec.MongoCodecs
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.util.concurrency.SemaphoreLimitedExecution
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
      |    host: "localhost"
      |    port: 27017
      |    srv: false
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
      |  consumer {
      |   config-yaml: "src/main/resources/config.yml"
      |   name: "supervisor"
      |   concurrency: 1
      |   queue: "supervisor"
      |   exchange: "doclib"
      |}
    """.stripMargin)
  implicit val m: Materializer = Materializer(system)
  implicit val codecs: CodecRegistry = MongoCodecs.get
  val mongo: Mongo = new Mongo()

  val wrappedCollection: JMongoCollection[DoclibDoc] = stub[JMongoCollection[DoclibDoc]]
  implicit val collection: MongoCollection[DoclibDoc] = MongoCollection[DoclibDoc](wrappedCollection)

  val readLimiter = SemaphoreLimitedExecution.create(5)
  val writeLimiter = SemaphoreLimitedExecution.create(5)

  implicit val appConfig: AppConfig =
    AppConfig(
      config.getString("consumer.name"),
      config.getInt("consumer.concurrency"),
      config.getString("consumer.queue"),
      Option(config.getString("consumer.exchange"))
    )

  private val handler = SupervisorHandler(readLimiter, writeLimiter)

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