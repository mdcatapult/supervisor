package io.mdcatapult.doclib.rules.sets

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.mongodb.scala.{Document ⇒ MongoDoc}

import scala.concurrent.ExecutionContextExecutor

object Text extends Rule {

  implicit val system: ActorSystem = ActorSystem("consumer-supervisor-image")
  implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val validDocuments: List[String] = List(
      "message/news", "message/rfc822", "text/aln", "text/calendar", "text/css", "text/fasta", "text/hkl",
      "text/html", "text/markdown", "text/matlab", "text/nex", "text/plain", "text/r", "text/rtf",
      "text/texmacs", "text/troff", "text/vcard", "text/x-asm", "text/x-awk", "text/x-bibtex", "text/x-c",
      "text/x-c++", "text/x-c++hdr", "text/x-c++src", "text/x-chdr", "text/x-csh", "text/x-csrc", "text/x-diff",
      "text/x-dsrc", "text/x-fortran", "text/x-gawk", "text/x-java", "text/x-lisp", "text/x-literate-haskell",
      "text/x-m4", "text/x-makefile", "text/x-msdos-batch", "text/x-objective-c", "text/x-pascal", "text/x-perl",
      "text/x-php", "text/x-po", "text/x-python", "text/x-ruby", "text/x-scala", "text/x-sfv", "text/x-sh",
      "text/x-shellscript", "text/x-tcl", "text/x-tex"
  )

  def unapply(doc: MongoDoc)(implicit config: Config): Option[Sendables] = {
    implicit val document: MongoDoc = doc
    if (!doc.contains("mimetype")) { None }
    else if (!validDocuments.contains(doc.getString("mimetype"))) { None }
    else if (completed("text")) { None }
    else if (started("text")) { Some(Sendables()) } // ensures requeue with supervisor
    else {
      NER.unapply(doc)
    }
  }

}
