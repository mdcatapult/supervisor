import sbtrelease.ReleaseStateTransformations._
import Release._

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val IntegrationTest = config("it") extend Test

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name              := "consumer-supervisor",
    scalaVersion      := "2.13.3",
    scalacOptions     ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint"
    ),
    useCoursier   := false,
    resolvers         ++= Seq(
      "gitlab" at "https://gitlab.com/api/v4/projects/50550924/packages/maven",
      "Maven Public" at "https://repo1.maven.org/maven2"),
    updateOptions     := updateOptions.value.withLatestSnapshots(latestSnapshots = false),
    credentials       += {
      sys.env.get("CI_JOB_TOKEN") match {
        case Some(p) =>
          Credentials("GitLab Packages Registry", "gitlab.com", "gitlab-ci-token", p)
        case None =>
          Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= {
      val doclibCommonVersion = "4.0.1"

      val configVersion = "1.4.2"
      val akkaVersion = "2.8.1"
      val catsVersion = "2.9.0"
      val scalacticVersion = "3.2.15"
      val scalaTestVersion = "3.2.15"
      val scalaMockVersion = "5.2.0"
      val scalaLoggingVersion = "3.9.5"
      val logbackClassicVersion = "1.4.7"
      val moultingYamlVersion = "0.4.2"
      val log4jVersion = "2.20.0"

      Seq(
        "net.jcazevedo" %% "moultingyaml"               % moultingYamlVersion,
        "org.scalactic" %% "scalactic"                  % scalacticVersion,
        "org.scalatest" %% "scalatest"                  % scalaTestVersion % "it, test",
        "org.scalamock" %% "scalamock"                  % scalaMockVersion % "it, test",
        "com.typesafe.akka" %% "akka-testkit"           % akkaVersion % "it, test",
        "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
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
