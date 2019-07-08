package io.mdcatapult.doclib.messages

import io.mdcatapult.klein.queue.Envelope
import play.api.libs.json.{Format, Json, Reads, Writes}

object SupervisorMsg {
  implicit val msgReader: Reads[SupervisorMsg] = Json.reads[SupervisorMsg]
  implicit val msgWriter: Writes[SupervisorMsg] = Json.writes[SupervisorMsg]
  implicit val msgFormatter: Format[SupervisorMsg] = Json.format[SupervisorMsg]
}

/**
  *
  * @param id id of the mongo document to check
  * @param reset list of exchanges to force processing
  */
case class SupervisorMsg(id: String, reset: Option[List[String]] = None) extends Envelope


