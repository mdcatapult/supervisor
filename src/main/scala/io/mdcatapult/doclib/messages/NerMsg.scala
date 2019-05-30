package io.mdcatapult.doclib.messages

import io.mdcatapult.klein.queue.Envelope
import play.api.libs.json.{Format, Json, Reads, Writes}


object NerMsg {
  implicit val msgReader: Reads[NerMsg] = Json.reads[NerMsg]
  implicit val msgWriter: Writes[NerMsg] = Json.writes[NerMsg]
  implicit val msgFormatter: Format[NerMsg] = Json.format[NerMsg]
}

case class NerMsg(id: String, requires: Option[List[String]]) extends Envelope


