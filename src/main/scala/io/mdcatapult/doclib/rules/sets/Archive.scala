package io.mdcatapult.doclib.rules.sets

import java.io.{BufferedInputStream, FileInputStream}

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.{DoclibMsg, SupervisorMsg}
import io.mdcatapult.klein.queue.{Envelope, Queue, Sendable}
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.bson.BsonBoolean
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util._
import scala.util.matching.Regex


object Archive extends Rule {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor-archive")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val isArchive: Regex =
    """(application/(gzip|vnd.ms-cab-compressed|x-(7z-compressed|ace-compressed|alz-compressed|apple-diskimage|arj|astrotite-afa|b1|bzip2|cfs-compressed|compress|cpio|dar|dgc-compressed|gca-compressed|gtar|lzh|lzip|lzma|lzop|lzx|par2|rar-compressed|sbx|shar|snappy-framed|stuffit|stuffitx|tar|xz|zoo)|zip))""".r

  val downstream = "doclib.unarchive"

  def unapply(doc: MongoDoc)(implicit config: Config): Option[Sendables] = {
      implicit val document: MongoDoc = doc
      if (!doc.contains("mimetype")) { None }
      else if (isArchive.findFirstIn(doc.getString("mimetype")).isEmpty) { None }
      else if (doc.contains("unarchived")) { None }
      else if (completed("unarchived")) { None }
      else if (started("unarchived")) { Some(Sendables()) } // ensures requeue with supervisor
      else {
        Try(new ArchiveStreamFactory().createArchiveInputStream(
          new BufferedInputStream(
            new FileInputStream(
              doc.getString("source")
            )
          )
        )) match {
          case Success(_) ⇒ Some(Sendables(
            Queue[DoclibMsg](downstream),
          ))
          case Failure(e) ⇒ throw e // throwing means msg will be dead-lettered
        }
      }
    }
}
