import sbtrelease.ReleaseStateTransformations._
import Release._

lazy val configVersion = "1.3.2"
lazy val akkaVersion = "2.6.4"
lazy val catsVersion = "2.1.0"
lazy val awsScalaVersion = "0.8.1"
lazy val tikaVersion = "1.24"
lazy val doclibCommonVersion = "0.0.67"

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val IntegrationTest = config("it") extend Test

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name              := "consumer-supervisor",
    scalaVersion      := "2.13.1",
    scalacOptions     ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint",
      "-Xfatal-warnings",
    ),
    useCoursier   := false,
    coverageEnabled   := false,
    resolvers         ++= Seq(
      "MDC Nexus Releases" at "https://nexus.mdcatapult.io/repository/maven-releases/",
      "MDC Nexus Snapshots" at "https://nexus.mdcatapult.io/repository/maven-snapshots/"),
    updateOptions     := updateOptions.value.withLatestSnapshots(latestSnapshots = false),
    credentials       += {
      sys.env.get("NEXUS_PASSWORD") match {
        case Some(p) =>
          Credentials("Sonatype Nexus Repository Manager", "nexus.mdcatapult.io", "gitlab", p)
        case None =>
          Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic"                  % "3.1.1",
      "org.scalatest" %% "scalatest"                  % "3.1.1" % "it, test",
      "org.scalamock" %% "scalamock"                  % "4.4.0" % "it, test",
      "com.typesafe.akka" %% "akka-testkit"           % akkaVersion % "it, test",
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.typesafe" % "config"                       % configVersion,
      "org.typelevel" %% "cats-macros"                % catsVersion,
      "org.typelevel" %% "cats-kernel"                % catsVersion,
      "org.typelevel" %% "cats-core"                  % catsVersion,
      "io.mdcatapult.doclib" %% "common"              % doclibCommonVersion,
      "org.apache.tika" % "tika-core"                 % tikaVersion,
      "org.apache.tika" % "tika-parsers"              % tikaVersion,
      "jakarta.ws.rs" % "jakarta.ws.rs-api"           % "2.1.4"
    ).map(
      _.exclude(org = "javax.ws.rs", name = "javax.ws.rs-api")
        .exclude(org = "com.sun.activation", name = "javax.activation")
        .exclude(org = "com.sun.activation", name = "registries")
        .exclude(org = "com.typesafe.play", name = "shaded-asynchttpclient")),
  )
  .settings(
    assemblyJarName := "consumer.jar",
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", _*) => MergeStrategy.first
      case PathList("javax", "activation", _*) => MergeStrategy.first
      case PathList("com", "sun", "activation", "registries", _*) => MergeStrategy.first
      case PathList("com", "sun", "activation", "viewers", _*) => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList("org", "apache", "commons", _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", _*) => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "public-suffix-list.txt" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == ".gitkeep" => MergeStrategy.discard
      case n if n.startsWith("application.conf") => MergeStrategy.concat
      case n if n.endsWith(".conf") => MergeStrategy.concat
      case meta(_) => MergeStrategy.first
      case "META-INF/jpms.args" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
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