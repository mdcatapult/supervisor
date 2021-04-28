package io.mdcatapult.doclib.consumers

import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, _}

import scala.util.Try

case class Workflow(stages: Array[Stage]) {

  def getStage(stageName: String): Option[Stage] = {
    this.stages.find(stage => stage.name == stageName)
  }

  def getMimetypes(stageName: String): Option[Array[String]] = {
    for {
      stage <- this.getStage(stageName)
      mimeTypes <- stage.value.find(rule => rule.name == "mimetypes")
    } yield mimeTypes.value
  }
}

case class Stage(name: String, value: Array[Rule])

case class Rule(name: String, value: Array[String])

object WorkflowYaml extends DefaultYamlProtocol {

  implicit val ruleFormat = yamlFormat2(Rule.apply)
  implicit val stageFormat = yamlFormat2(Stage.apply)
  implicit val workflowFormat = yamlFormat1(Workflow.apply)

  def parseYaml(filepath: String): Try[Workflow] = {
    Try {
      val source = scala.io.Source.fromFile(filepath)
      val workflowYaml = source.mkString
      source.close()
      workflowYaml.parseYaml.convertTo[Workflow]
    }
  }
}


