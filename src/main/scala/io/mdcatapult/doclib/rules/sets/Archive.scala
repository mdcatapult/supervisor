package io.mdcatapult.doclib.rules.sets

import com.typesafe.config.Config
import io.mdcatapult.doclib.messages.DoclibMsg
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.traits.SupervisorRule
import io.mdcatapult.klein.queue.Registry

object Archive extends SupervisorRule[DoclibMsg] {

  val validMimetypes = List(
    "application/gzip",
    "application/vnd.ms-cab-compressed",
    "application/x-7z-compressed",
    "application/x-ace-compressed",
    "application/x-alz-compressed",
    "application/x-apple-diskimage",
    "application/x-arj",
    "application/x-astrotite-afa",
    "application/x-b1",
    "application/x-bzip2",
    "application/x-cfs-compressed",
    "application/x-compress",
    "application/x-cpio",
    "application/x-dar",
    "application/x-dgc-compressed",
    "application/x-gca-compressed",
    "application/x-gtar",
    "application/x-lzh",
    "application/x-lzip",
    "application/x-lzma",
    "application/x-lzop",
    "application/x-lzx",
    "application/x-par2",
    "application/x-rar-compressed",
    "application/x-sbx",
    "application/x-shar",
    "application/x-snappy-framed",
    "application/x-stuffit",
    "application/x-stuffitx",
    "application/x-tar",
    "application/x-xz",
    "application/x-zoo",
    "application/zip"
  )

  def unapply(doc: DoclibDoc)
             (implicit config: Config, registry: Registry[DoclibMsg])
  : Option[(String, Sendables)] = {

    implicit val document: DoclibDoc = doc

    if (!validMimetypes.contains(doc.mimetype))
      None
    else if (completed("supervisor.archive"))
      None
    else
      Some(getSendables("supervisor.archive"))
  }
}
