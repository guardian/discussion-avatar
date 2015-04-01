import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object AvatarBuild extends Build {
  val Organization = "com.gu"
  val Name = "avatar"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.5"
  val ScalatraVersion = "2.3.0"
  
  val jettyVersion = "9.1.5.v20140505"
  val json4sVersion = "3.2.10"
  val logbackVersion = "1.1.2"
  val servletApiVersion = "3.1.0"
  val scalazVersion = "7.1.1"
  val identityCookieVersion = "3.44"
  val typesafeConfigVersion = "1.2.1"

  val guardianReleases = "Guardian releases" at "http://guardian.github.io/maven/repo-releases"

  lazy val project = Project (
    Name,
    file("."),
    settings = ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ Seq(
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
        "com.typesafe" % "config" % typesafeConfigVersion
      )
    )
  )
}
