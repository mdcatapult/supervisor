package io.mdcatapult.doclib.messages

import io.mdcatapult.klein.queue.Envelope
import play.api.libs.json.{Format, Json, Reads, Writes}

object SupervisorMsg {
  implicit val msgReader: Reads[SupervisorMsg] = Json.reads[SupervisorMsg]
  implicit val msgWriter: Writes[SupervisorMsg] = Json.writes[SupervisorMsg]
  implicit val msgFormatter: Format[SupervisorMsg] = Json.format[SupervisorMsg]
}

case class SupervisorMsg(id: String) extends Envelope


