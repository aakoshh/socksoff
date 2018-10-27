import scala.sys.process._

def versionFromGit = {
  val Version = """v(\d+\..*)""".r // Get rid of the leading "v"
  ("git tag -l --points-at HEAD" #| "grep -e ." #|| "git rev-parse --short HEAD" !!).trim match {
    case Version(semver) => semver
    case ver => ver
  }
}

// version in ThisBuild := versionFromGit
version in ThisBuild := "0.1-SNAPSHOT"

scalaVersion in ThisBuild := "2.11.8"



// Define a sub-module
def proj(
  folder: String,
  idOpt: Option[String] = None,
  // Add logging for the projects that are runnable entry points.
  hasMain: Boolean = false) = {
  val id = idOpt getOrElse folder.replace("/","-")
  var project = Project(
    id = id,
    base = file(folder))
  .settings(
    name := s"socksoff-$id",
    organization := "com.socksoff")
  .configs(IntegrationTest) // run them like `it:test`
  .settings(
    Defaults.itSettings,
    resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % VersionOf.`org.scalatest` % "it,test",
      // Add a default logger so we can see logs during tests if necessary.
      "ch.qos.logback" % "logback-classic" % VersionOf.`logback` % "it,test"),
    evictionWarningOptions in update :=
      EvictionWarningOptions.default
        .withWarnTransitiveEvictions(false)
        .withWarnDirectEvictions(false))

  if (hasMain) {
    project = project.settings(
      libraryDependencies ++= Seq(
        // For default logging to stdout.
        "ch.qos.logback" % "logback-classic" % VersionOf.`logback`
      ),
      // Copy the common logging file during packing.
      packResourceDir += (file(".") / "pack" -> ""))
  }

  project
}

enablePlugins(PackPlugin)

// Runnable SOCKS5 server
lazy val server = proj("server", hasMain = true)
