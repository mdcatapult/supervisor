package io.mdcatapult.doclib.rules.legacy.sets

import java.io.{BufferedInputStream, FileInputStream}

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.rules.sets.{Rule, Sendables}
import io.mdcatapult.klein.queue.Exchange
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor
import scala.util._
import scala.util.matching.Regex


object Archive extends Rule {

  val allowedArchives = List(
    "application/gzip", "application/vnd.ms-cab-compressed", "application/x-7z-compressed",
    "application/x-ace-compressed", "application/x-alz-compressed", "application/x-apple-diskimage",
    "application/x-arj", "application/x-astrotite-afa", "application/x-b1", "application/x-bzip2",
    "application/x-cfs-compressed", "application/x-compress", "application/x-cpio", "application/x-dar",
    "application/x-dgc-compressed", "application/x-gca-compressed", "application/x-gtar", "application/x-lzh",
    "application/x-lzip", "application/x-lzma", "application/x-lzop", "application/x-lzx", "application/x-par2",
    "application/x-rar", "application/x-rar-compressed", "application/x-sbx", "application/x-shar",
    "application/x-snappy-framed", "application/x-stuffit", "application/x-stuffitx", "application/x-tar",
    "application/x-xz", "application/x-zoo", "application/zip", "application/zlib"
  )

  val downstream = "klein.extraction"

  def unapply(doc: MongoDoc)(implicit config: Config, sys: ActorSystem, ex: ExecutionContextExecutor): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype"))
      None
    else if (!allowedArchives.contains(doc.getString("mimetype")))
      None
    else if (completed("extraction"))
      None
    else if (started("extraction"))
      Some(Sendables()) // ensures requeue with supervisor
    else
      Some(Sendables(Exchange[DoclibMsg](downstream)))
  }
}
