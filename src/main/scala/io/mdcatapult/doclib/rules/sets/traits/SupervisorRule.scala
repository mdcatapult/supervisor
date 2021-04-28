package io.mdcatapult.doclib.rules.sets.traits

import java.time.ZoneOffset

import com.typesafe.config.Config
import io.mdcatapult.doclib.models.{DoclibDoc, DoclibFlag}
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

import scala.jdk.CollectionConverters._

trait SupervisorRule[T <: Envelope] {

  /**
    * tests if all flags for key have been completed
    * @param key String flag to find
    * @param doc Document to check
    * @param config Config object to retrieve flag base path from
    * @return
    */
  def completed(key: String)(implicit doc: DoclibDoc, config: Config): Boolean =
    config.getConfigList(s"$key.required").asScala.forall(r => doc.getFlag(r.getString("flag")).exists(_.ended.nonEmpty))


  /**
    * tests if all flags for key have been started
    * @param key String flag to check
    * @param doc Document to check
    * @param config Config object to retrieve flag base path from
    * @return
    */
  def started(key: String)(implicit doc: DoclibDoc, config: Config): Boolean =
    config.getConfigList(s"$key.required").asScala.forall(r => doc.getFlag(r.getString("flag")).nonEmpty)


  /**
    * checks the document for all configured & required flags and generates a list of
    * sendables that do not already have flags present in the document,
    * will always return an empty Sendables list which will always result in requeue
    * @param key String of the config path
    * @param doc Document to check
    * @param config Config to retrieve settings from
    * @param registry Registry
    * @return
    */
  def getSendables(key: String)
                  (implicit doc: DoclibDoc, config: Config, registry: Registry[T])
  : (String, Sendables) =
    (key, config.getConfigList(s"$key.required").asScala
      .filter(sendableAllowed)
      .map(r => r.getString("type") match {
        case "queue" => registry.get(r.getString("route"))
        case _ => throw new Exception(s"Unable to handle configured type '${r.getString("type")}' for required flag $key")
      }).toList.asInstanceOf[Sendables])

  /**
    * Allow Sendable if there is no existing flag or if flag exists and has reset and
    * reset timestamp is more recent than started.
    * Always queue to "analytical.supervisor" regardless of flag state
    *
    * @param flagConfig config
    * @param doc doc
    * @return
    */
  def sendableAllowed(flagConfig: Config)(implicit doc: DoclibDoc, config: Config): Boolean = {
    if (flagConfig.getString("flag") == config.getString("analytical.name")) {
        true
    } else if (doc.hasFlag(flagConfig.getString("flag"))) {
      val flag: DoclibFlag = doc.getFlag(flagConfig.getString("flag")).head
      flag.reset match {
        case Some(time) => time.toEpochSecond(ZoneOffset.UTC) > flag.started.get.toEpochSecond(ZoneOffset.UTC)
        case None => false
      }
    } else {
      true
    }
  }

  def doTask(key: String, doc: DoclibDoc)(implicit config: Config, registry: Registry[T]): Option[(String, Sendables)] = {
    implicit val document: DoclibDoc = doc
    getSendables(key) match {
      case (s, head::rest) => Some((s, head::rest))
      case _ => None
    }
  }

}
