import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

enablePlugins(
  RiffRaffArtifact,
  UniversalPlugin,
  JavaAppPackaging,
  JettyPlugin
)

organization := "com.gu"
name := "avatar-api"
version := "1.0"
scalaVersion := "2.11.11"

val ScalatraVersion = "2.3.0"
val jettyVersion = "9.1.5.v20140505"
val json4sVersion = "3.2.10"
val logbackVersion = "1.1.6"
val logstashEncoderVersion = "4.6"
val servletApiVersion = "3.1.0"
val scalazVersion = "7.1.1"
val identityCookieVersion = "3.44"
val typesafeConfigVersion = "1.2.1"
val amazonawsVersion = "1.11.26"
val scalaLoggingVersion = "3.1.0"
val apacheCommonsVersion = "3.4"

val guardianReleases = "Guardian releases" at "http://guardian.github.io/maven/repo-releases"

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
  "org.json4s"   %% "json4s-native" % json4sVersion,
  "org.json4s"   %% "json4s-jackson" % json4sVersion,
  "org.scalatra" %% "scalatra-json" % ScalatraVersion,
  "org.scalaz" %% "scalaz-core" % scalazVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
  "org.scalatra" %% "scalatra-swagger"  % ScalatraVersion,
  "com.gu.identity" %% "identity-cookie" % identityCookieVersion,
  "com.typesafe" % "config" % typesafeConfigVersion,
  "com.amazonaws" % "aws-java-sdk" % amazonawsVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.apache.commons" % "commons-lang3" % apacheCommonsVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "0.8"
)

sourceDirectory in webappPrepare := (sourceDirectory in Compile).value / "resources/webapp"

containerPort := 8900

mainClass in Compile := Some("com.gu.adapters.http.JettyLauncher")

// package stuff - note, assumes presence of cfn and rr files
packageName in Universal := normalizedName.value
riffRaffPackageType := (packageZipTarball in Universal).value
mappings in Universal ++= directory("conf")
riffRaffArtifactResources += (file("platform/cloudformation/discussion-avatar-api.yaml"), "cfn/avatar-api.yaml")
riffRaffArtifactResources += (file("platform/riff-raff.yaml"), "riff-raff.yaml")
riffRaffArtifactResources += (riffRaffPackageType.value -> s"${name.value}/${name.value}.tgz")
