
libraryDependencies ++= Seq(
  "io.github.fengyouchao" % "sockslib" % VersionOf.sockslib,
  "com.typesafe.scala-logging" %% "scala-logging" % VersionOf.`typesafe-logging`
)

excludeDependencies += ExclusionRule("*", "slf4j-log4j12")
excludeDependencies += ExclusionRule("org.apache.logging.log4j", "*")
