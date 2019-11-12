package io.mdcatapult.doclib.rules.sets.traits

import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

trait NER[T <: Envelope] extends SupervisorRule[T]{

  val validMimetypes: List[String] = List(
    "message/news", "message/rfc822", "text/aln", "text/calendar", "text/css", "text/fasta", "text/hkl",
    "text/html", "text/markdown", "text/matlab", "text/nex", "text/plain", "text/r", "text/rtf",
    "text/texmacs", "text/troff", "text/vcard", "text/x-asm", "text/x-awk", "text/x-bibtex", "text/x-c",
    "text/x-c++", "text/x-c++hdr", "text/x-c++src", "text/x-chdr", "text/x-csh", "text/x-csrc", "text/x-diff",
    "text/x-dsrc", "text/x-fortran", "text/x-gawk", "text/x-java", "text/x-lisp", "text/x-literate-haskell",
    "text/x-m4", "text/x-makefile", "text/x-msdos-batch", "text/x-objective-c", "text/x-pascal", "text/x-perl",
    "text/x-php", "text/x-po", "text/x-python", "text/x-ruby", "text/x-scala", "text/x-sfv", "text/x-sh",
    "text/x-shellscript", "text/x-tcl", "text/x-tex"
  )
  /**
    * convenience function to automatically test if NER required and return appropriate sendables
    * @param doc Document To Test
    * @param config  Config
    * @param registry Registry
    * @return
    */
  def requiredNer()(implicit doc: DoclibDoc, config: Config, registry: Registry[T]): Option[Sendables] = {
    validMimetypes.contains(doc.mimetype) match {
      case true => doTask("supervisor.ner", doc)
      case false => None
    }
  }

}
