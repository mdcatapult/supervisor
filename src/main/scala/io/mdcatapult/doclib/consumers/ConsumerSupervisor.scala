package io.mdcatapult.doclib.consumers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.spingo.op_rabbit.SubscriptionRef
import io.mdcatapult.doclib.consumer.AbstractConsumer
import io.mdcatapult.doclib.handlers.SupervisorHandler
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.klein.mongo.Mongo
import io.mdcatapult.klein.queue.{Queue, Registry}
import io.mdcatapult.util.admin.{Server => AdminServer}
import org.mongodb.scala._

import scala.util.{Failure, Success}


/**
  * RabbitMQ Consumer to handle NER detection using leadmine for documents in MongoDB
  */
object ConsumerSupervisor extends AbstractConsumer {

  def start()(implicit as: ActorSystem, m: Materializer, mongo: Mongo): SubscriptionRef = {
    import as.dispatcher

    AdminServer(config).start()

    implicit val collection: MongoCollection[DoclibDoc] =
      mongo.getCollection(config.getString("mongo.doclib-database"), config.getString("mongo.documents-collection"))

    val upstream: Queue[SupervisorMsg] = queue("consumer.queue")

    implicit val workflow = WorkflowYaml.parseYaml(config.getString("consumer.config-yaml")) match {
      case Failure(exception) => throw exception
      case Success(value) => value
    }

    implicit val registry: Registry[DoclibMsg] = new Registry[DoclibMsg]()

    upstream.subscribe(
      SupervisorHandler().handle,
      config.getInt("consumer.concurrency")
    )
  }
}
