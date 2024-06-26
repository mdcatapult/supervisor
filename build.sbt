import sbtrelease.ReleaseStateTransformations._
import Release._

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val scala_2_13 = "2.13.14"

val doclibCommonVersion = "5.0.1"

val configVersion = "1.4.3"
val pekkoVersion = "1.0.2"
val catsVersion = "2.12.0"
val scalacticVersion = "3.2.15"
val scalaTestVersion = "3.2.15"
val scalaMockVersion = "6.0.0"
val scalaLoggingVersion = "3.9.5"
val logbackClassicVersion = "1.5.6"
val moultingYamlVersion = "0.4.2"
val log4jVersion = "2.23.1"

lazy val creds = {
  sys.env.get("CI_JOB_TOKEN") match {
    case Some(token) =>
      Credentials("GitLab Packages Registry", "gitlab.com", "gitlab-ci-token", token)
    case _ =>
      Credentials(Path.userHome / ".sbt" / ".credentials")
  }
}

// Registry ID is the project ID of the project where the package is published, this should be set in the CI/CD environment
val registryId = sys.env.get("REGISTRY_HOST_PROJECT_ID").getOrElse("")

lazy val root = (project in file("."))
  .settings(
    name              := "consumer-supervisor",
    scalaVersion      := scala_2_13,
    scalacOptions     ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint"
    ),
    useCoursier   := false,
    resolvers ++= Seq(
      "gitlab" at s"https://gitlab.com/api/v4/projects/$registryId/packages/maven",
      "Maven Public" at "https://repo1.maven.org/maven2"),
    publishTo := {
      Some("gitlab" at s"https://gitlab.com/api/v4/projects/$registryId/packages/maven")
    },
    credentials += creds,
    libraryDependencies ++= {
      Seq(
        "net.jcazevedo" %% "moultingyaml"               % moultingYamlVersion,
        "org.scalactic" %% "scalactic"                  % scalacticVersion,
        "org.scalatest" %% "scalatest"                  % scalaTestVersion % Test,
        "org.scalamock" %% "scalamock"                  % scalaMockVersion % Test,
        "org.apache.pekko" %% "pekko-testkit"           % pekkoVersion % Test,
        "org.apache.pekko" %% "pekko-slf4j"             % pekkoVersion,
        "ch.qos.logback" % "logback-classic"            % logbackClassicVersion,
        "org.apache.logging.log4j" % "log4j-core"       % log4jVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
        "com.typesafe" % "config"                       % configVersion,
        "org.typelevel" %% "cats-kernel"                % catsVersion,
        "org.typelevel" %% "cats-core"                  % catsVersion,
        "io.mdcatapult.doclib" %% "common"              % doclibCommonVersion
    )
    }.map(
      _.exclude(org = "javax.ws.rs", name = "javax.ws.rs-api")
        .exclude(org = "com.sun.activation", name = "javax.activation")
        .exclude(org = "com.sun.activation", name = "registries")
        .exclude(org = "com.google.protobuf", name = "protobuf-java")
        .exclude(org = "com.typesafe.play", name = "shaded-asynchttpclient")),
  )
  .settings(
    assemblyJarName := "consumer.jar",
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("javax", "servlet", _*) => MergeStrategy.first
      case PathList("javax", "activation", _*) => MergeStrategy.first
      case PathList("com", "sun", "activation", "registries", _*) => MergeStrategy.first
      case PathList("com", "sun", "activation", "viewers", _*) => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList("org", "apache", "commons", _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", _*) => MergeStrategy.first
      case PathList("scala", "collection", "compat", _*) => MergeStrategy.first
      case PathList("scala", "util", "control", "compat", _*) => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "public-suffix-list.txt" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == ".gitkeep" => MergeStrategy.discard
      case n if n.startsWith("application.conf") => MergeStrategy.first
      case n if n.endsWith(".conf") => MergeStrategy.concat
      case n if n.startsWith("logback.xml") => MergeStrategy.first
      case n if n.startsWith("scala-collection-compat.properties") => MergeStrategy.first
      case meta(_) => MergeStrategy.first
      case "META-INF/jpms.args" => MergeStrategy.discard
      case "META-INF/io.netty.versions.properties" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
  .settings(
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      getShortSha,
      writeReleaseVersionFile,
      commitAllRelease,
      tagRelease,
      runAssembly,
      setNextVersion,
      writeNextVersionFile,
      commitAllNext,
      pushChanges
    )
  )

lazy val it = project
  .in(file("it"))  //it test located in a directory named "it"
  .settings(
    name := "consumer-supervisor-it",
    scalaVersion := "2.13.14",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestVersion,
        "org.scalamock" %% "scalamock" % scalaMockVersion,
        "org.apache.pekko" %% "pekko-testkit" % pekkoVersion
      )
    }
  )
  .dependsOn(root)
