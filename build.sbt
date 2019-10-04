

lazy val configVersion = "1.3.2"
lazy val akkaVersion = "2.5.25"
lazy val catsVersion = "2.0.0"
lazy val opRabbitVersion = "2.1.0"
lazy val mongoVersion = "2.5.0"
lazy val awsScalaVersion = "0.8.1"
lazy val tikaVersion = "1.21"

val meta = """META.INF/(blueprint|cxf).*""".r

lazy val root = (project in file(".")).
  settings(
    name              := "consumer-supervisor",
    version           := "0.1",
    scalaVersion      := "2.12.8",
    scalacOptions     += "-Ypartial-unification",
    coverageEnabled   := true,
    resolvers         ++= Seq("MDC Nexus" at "http://nexus.mdcatapult.io/repository/maven-releases/"),
    credentials       += {
      val nexusPassword = sys.env.get("NEXUS_PASSWORD")
      if ( nexusPassword.nonEmpty ) {
        Credentials("Sonatype Nexus Repository Manager", "nexus.mdcatapult.io", "gitlab", nexusPassword.get)
      } else {
        Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic"                  % "3.0.8",
      "org.scalatest" %% "scalatest"                  % "3.0.8" % Test,
      "org.scalamock" %% "scalamock"                  % "4.3.0" % Test,
      "com.typesafe.akka" %% "akka-testkit"           % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "com.typesafe" % "config"                       % configVersion,
      "org.typelevel" %% "cats-macros"                % catsVersion,
      "org.typelevel" %% "cats-kernel"                % catsVersion,
      "org.typelevel" %% "cats-core"                  % catsVersion,
      "io.mdcatapult.doclib" %% "common"              % "0.0.17-SNAPSHOT",
      "org.apache.tika" % "tika-core"                 % tikaVersion,
      "org.apache.tika" % "tika-parsers"              % tikaVersion,
      "jakarta.ws.rs" % "jakarta.ws.rs-api"           % "2.1.4"
    )
      .map(_ exclude("javax.ws.rs", "javax.ws.rs-api"))
      .map(_ exclude("com.sun.activation", "javax.activation"))
      .map(_ exclude("com.sun.activation", "registries")),
    assemblyJarName := "consumer-supervisor.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.first
      case PathList("com", "sun", "activation", "registries", xs @ _*) => MergeStrategy.first
      case PathList("com", "sun", "activation", "viewers", xs @ _*) => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "module-info.class" => MergeStrategy.first
      case PathList("org", "apache", "commons", xs @ _*) => MergeStrategy.first
      case PathList("com", "ctc", "wstx", xs @ _*) => MergeStrategy.first
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
