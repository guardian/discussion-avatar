import com.mojolly.scalate.ScalatePlugin._
import org.scalatra.sbt._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly._
import com.typesafe.sbt.SbtScalariform.{ScalariformKeys, scalariformSettings}
import scalariform.formatter.preferences._

object AvatarBuild extends Build {
  val Organization = "com.gu"
  val Name = "avatar"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.5"
  val ScalatraVersion = "2.3.0"
  
  val jettyVersion = "9.1.5.v20140505"
  val json4sVersion = "3.2.10"
  val logbackVersion = "1.1.3"
  val servletApiVersion = "3.1.0"
  val scalazVersion = "7.1.1"
  val identityCookieVersion = "3.44"
  val typesafeConfigVersion = "1.2.1"
  val amazonawsVersion = "1.9.6"
  val scalaLoggingVersion = "3.1.0"

  val guardianReleases = "Guardian releases" at "http://guardian.github.io/maven/repo-releases"

  lazy val project = Project (
    Name,
    file("."),
    settings = ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ scalariformSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      //scalacOptions += "-Ylog-classpath",
      resolvers += Classpaths.typesafeReleases,
      resolvers += guardianReleases,
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "ch.qos.logback" % "logback-classic" % logbackVersion,
        "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % "container;compile",
        "org.eclipse.jetty" % "jetty-plus" % jettyVersion % "container",
        "javax.servlet" % "javax.servlet-api" % servletApiVersion,
        "org.json4s"   %% "json4s-native" % json4sVersion,
        "org.json4s"   %% "json4s-jackson" % json4sVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalaz" %% "scalaz-core" % scalazVersion,
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-swagger"  % ScalatraVersion,
        "com.gu.identity" %% "identity-cookie" % identityCookieVersion,
        "com.typesafe" % "config" % typesafeConfigVersion,
        "com.amazonaws" % "aws-java-sdk" % amazonawsVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion

      ),
      assemblyMergeStrategy in assembly := {
        case "version.txt" => MergeStrategy.discard
        case "mime.types" => MergeStrategy.discard
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      ScalariformKeys.preferences := ScalariformKeys.preferences.value
        .setPreference(DoubleIndentClassDeclaration, true)
    )
  )
}
