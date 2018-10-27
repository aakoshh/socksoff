
libraryDependencies ++= Seq(
  "io.github.fengyouchao" % "sockslib" % VersionOf.sockslib
)

libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % VersionOf.`typesafe-logging`
)// .map(_.exclude("*", "slf4j-log4j12"))

excludeDependencies += ExclusionRule("*", "slf4j-log4j12")
excludeDependencies += ExclusionRule("org.apache.logging.log4j", "*")


val mains = Map("server" -> "com.socksoff.server.ServerMain")
packMain := mains
packExtraClasspath := mains.keys.map(_ -> Seq(
  // Add the pack dir to the classpath so logback.xml is detected.
  "${PROG_HOME}/",
  // Add a /deps directory for JARs so we can copy JARs in 2 steps.
  "${PROG_HOME}/deps/*"
)).toMap

fork in IntegrationTest := false