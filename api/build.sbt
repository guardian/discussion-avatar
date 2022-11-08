import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import scalariform.formatter.preferences._

enablePlugins(
  RiffRaffArtifact,
  UniversalPlugin,
  JavaAppPackaging,
  JettyPlugin
)

organization := "com.gu"
name := "avatar-api"
version := "1.0"
scalaVersion := "2.12.8"

val ScalatraVersion = "2.6.3"
val jettyVersion = "9.2.28.v20190418"
val json4sVersion = "3.5.2"
val logbackVersion = "1.2.0"
val logstashEncoderVersion = "4.9"
val servletApiVersion = "3.1.0"
val scalazVersion = "7.1.17"
val identityVersion = "3.255"
val typesafeConfigVersion = "1.2.1"
val amazonawsVersion = "1.11.1034"
val scalaLoggingVersion = "3.6.0"
val apacheCommonsVersion = "3.4"

val guardianReleases = "Guardian releases" at "https://guardian.github.io/maven/repo-releases"

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
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % Test,
  "org.mockito" % "mockito-core" % "3.1.0" % Test,
  "org.scalatra" %% "scalatra-swagger"  % ScalatraVersion,
  "com.gu.identity" %% "identity-auth-core" % identityVersion,
  "com.typesafe" % "config" % typesafeConfigVersion,
  "com.amazonaws" % "aws-java-sdk-ses" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-sqs" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-sns" % amazonawsVersion,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % amazonawsVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.apache.commons" % "commons-lang3" % apacheCommonsVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "3.0.4",
  "com.typesafe.akka" %% "akka-http" % "10.1.15", 
  "com.typesafe.akka" %% "akka-http-core" % "10.1.15",
  "com.gu" % "kinesis-logback-appender" % "1.4.2"
)

sourceDirectory in webappPrepare := (sourceDirectory in Compile).value / "resources/webapp"

containerPort := 8900

mainClass in Compile := Some("com.gu.adapters.http.JettyLauncher")

// package stuff - note, assumes presence of cfn and rr files
packageName in Universal := normalizedName.value
riffRaffPackageType := (packageZipTarball in Universal).value
mappings in Universal ++= directory("conf")
// See the README (## Deploying the app) to understand how the *.yaml files are provided at build time.
riffRaffArtifactResources += (file("platform/cloudformation/discussion-avatar-api.yaml"), "cfn/avatar-api.yaml")
riffRaffArtifactResources += (file("platform/riff-raff.yaml"), "riff-raff.yaml")
riffRaffArtifactResources += (riffRaffPackageType.value -> s"${name.value}/${name.value}.tgz")
scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(SpacesAroundMultiImports, false)