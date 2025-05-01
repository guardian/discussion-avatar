import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import scalariform.formatter.preferences._

enablePlugins(
  UniversalPlugin,
  JavaAppPackaging,
  JettyPlugin
)

organization := "com.gu"
name := "avatar-api"
version := "1.0"
scalaVersion := "2.12.18"

val ScalatraVersion = "2.6.3"
val jettyVersion = "9.4.56.v20240826"
val json4sVersion = "3.5.2"
val logbackVersion = "1.2.13"
val logstashEncoderVersion = "7.3"
val servletApiVersion = "3.1.0"
val identityVersion = "4.31"
val typesafeConfigVersion = "1.2.1"
val amazonawsVersion = "1.12.668"
val scalaLoggingVersion = "3.6.0"
val apacheCommonsVersion = "3.4"

val guardianReleases =
  "Guardian releases" at "https://guardian.github.io/maven/repo-releases"

scalacOptions ++= Seq("-feature", "-deprecation", "-target:jvm-1.8")

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  guardianReleases
)

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "ch.qos.logback" % "logback-access" % logbackVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % logstashEncoderVersion,
  "org.eclipse.jetty" % "jetty-webapp" % jettyVersion % "container;compile",
  "org.eclipse.jetty" % "jetty-plus" % jettyVersion % "container",
  "javax.servlet" % "javax.servlet-api" % servletApiVersion,
  "org.json4s" %% "json4s-native" % json4sVersion,
  "org.json4s" %% "json4s-jackson" % json4sVersion,
  "org.scalatra" %% "scalatra-json" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % Test,
  "org.mockito" % "mockito-core" % "3.1.0" % Test,
  "org.scalatra" %% "scalatra-swagger" % ScalatraVersion,
  "com.gu.identity" %% "identity-auth-core" % identityVersion,
  "com.typesafe" % "config" % typesafeConfigVersion,
  "com.amazonaws" % "aws-java-sdk-ses" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-sqs" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % amazonawsVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.apache.commons" % "commons-lang3" % apacheCommonsVersion,
  "org.apache.pekko" %% "pekko-connectors-sqs" % "1.0.0"
)

// Exclude all transitive Akka dependencies
libraryDependencies ~= { deps =>
  deps.map(_.excludeAll(ExclusionRule(organization = "com.typesafe.akka")))
}

// Scalatra has not updated to scala-xml 2.0.0 yet.
// Tell SBT to ignore the version conflict. This is fairly accepted practice for scala-xml: https://github.com/sbt/sbt/issues/6997
// Long term fix is that we should upgrade to Scalatra 3.x
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

webappPrepare / sourceDirectory := (Compile / sourceDirectory).value / "resources/webapp"

containerPort := 8900

Compile / mainClass := Some("com.gu.adapters.http.JettyLauncher")

// package stuff - note, assumes presence of cfn and rr files
Universal / packageName := normalizedName.value
Universal / mappings ++= directory("conf")
scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(SpacesAroundMultiImports, false)
