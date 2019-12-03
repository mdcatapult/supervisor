package io.mdcatapult.doclib.rules.sets.traits

import com.typesafe.config.Config
import io.mdcatapult.doclib.models.DoclibDoc
import io.mdcatapult.doclib.rules.sets.Sendables
import io.mdcatapult.klein.queue.{Envelope, Registry}

import scala.collection.JavaConverters._

trait SupervisorRule[T <: Envelope] {

  /**
    * Abstract unapply
    * @param doc Document to be matched
    * @param config Config
    * @param registry Registry
    * @return
    */
  def unapply(doc: DoclibDoc)(implicit config: Config, registry: Registry[T]): Option[Sendables]

  /**
    * tests if all flags for key have been completed
    * @param key String flag to find
    * @param doc Document to check
    * @param config Config object to retrieve flag base path from
    * @return
    */
  def completed(key: String)(implicit doc: DoclibDoc, config: Config): Boolean =
    config.getConfigList(s"$key.required").asScala.forall(r ⇒ doc.getFlag(r.getString("flag")).exists(_.ended.nonEmpty))


  /**
    * tests if all flags for key have been started
    * @param key String flag to check
    * @param doc Document to check
    * @param config Config object to retrieve flag base path from
    * @return
    */
  def started(key: String)(implicit doc: DoclibDoc, config: Config): Boolean =
    config.getConfigList(s"$key.required").asScala.forall(r ⇒ doc.getFlag(r.getString("flag")).nonEmpty)


  /**
    * checks the document for all configured & required flags and generates a list of
    * sendables that do not already have flags present in the document,
    * will always return an empty Sendables list which will always result in requeue
    * @param key String ofg the config path
    * @param doc Document to check
    * @param config Config to retrieve settings from
    * @param registry Registry
    * @return
    */
  def getSendables(key: String)
                  (implicit doc: DoclibDoc, config: Config, registry: Registry[T])
  : Sendables =
    config.getConfigList(s"$key.required").asScala
      .filterNot(r ⇒ doc.hasFlag(r.getString("flag")))
      .map(r ⇒ r.getString("type") match {
        case "queue" ⇒ registry.get(r.getString("route"))
        case _ ⇒ throw new Exception(s"Unable to handle configured type '${r.getString("type")}' for required flag $key")
      }).toList.asInstanceOf[Sendables]


  def doTask(key: String, doc: DoclibDoc)(implicit config: Config, registry: Registry[T]): Option[Sendables] = {
    implicit val document: DoclibDoc = doc
    if (started(key) && !completed(key))
      Some(Sendables())
    else if (!started(key))
      Some(getSendables(key))
    else
      None
  }

}
