import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import scalariform.formatter.preferences._

enablePlugins(
  UniversalPlugin,
  JavaAppPackaging
)

organization := "com.gu"
name := "avatar-api"
version := "1.0"
scalaVersion := "2.13.16"

val ScalatraVersion = "3.1.1"
val jettyVersion = "12.0.20"
val json4sVersion = "4.1.0"
val logbackVersion = "1.5.18"
val logbackAccessVersion = "2.0.6"
val logstashEncoderVersion = "8.1"
val servletApiVersion = "6.0.0"
val identityVersion = "4.31"
val typesafeConfigVersion = "1.4.3"
val amazonawsVersion = "2.34.5"
val scalaLoggingVersion = "3.9.5"
val apacheCommonsVersion = "3.17.0"

val guardianReleases =
  "Guardian releases" at "https://guardian.github.io/maven/repo-releases"

scalacOptions ++= Seq("-feature", "-deprecation", "-target:jvm-1.8")

resolvers ++= Seq(
  Classpaths.typesafeReleases,
  guardianReleases
)

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra-jakarta" % ScalatraVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "ch.qos.logback.access" % "logback-access-jetty12" % logbackAccessVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % logstashEncoderVersion,
  "org.eclipse.jetty.ee10" % "jetty-ee10-servlet" % jettyVersion,
  "org.eclipse.jetty" % "jetty-plus" % jettyVersion,
  "jakarta.servlet" % "jakarta.servlet-api" % servletApiVersion,
  "io.github.json4s" %% "json4s-native" % json4sVersion,
  "io.github.json4s" %% "json4s-jackson" % json4sVersion,
  "io.github.json4s" %% "json4s-ext" % json4sVersion,
  "org.scalatra" %% "scalatra-json-jakarta" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest-jakarta" % ScalatraVersion % Test,
  "org.mockito" % "mockito-core" % "5.17.0" % Test,
  "org.scalatestplus" %% "mockito-5-12" % "3.2.19.0" % Test,
  "org.scalatra" %% "scalatra-swagger-jakarta" % ScalatraVersion,
  "com.gu.identity" %% "identity-auth-core" % identityVersion,
  "com.typesafe" % "config" % typesafeConfigVersion,
  "software.amazon.awssdk" % "ses" % amazonawsVersion,
  "software.amazon.awssdk" % "sqs" % amazonawsVersion,
  "software.amazon.awssdk" % "s3" % amazonawsVersion,
  "software.amazon.awssdk" % "sns" % amazonawsVersion,
  "software.amazon.awssdk" % "dynamodb" % amazonawsVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.apache.commons" % "commons-lang3" % apacheCommonsVersion,
  "org.apache.pekko" %% "pekko-connectors-sqs" % "1.0.0"
)

// Transient Dependency Overrides
dependencyOverrides ++= Seq(
  // identity-auth-core depends on jackson-module-scala 2.15 which forces Jackson 2.15
  // Should be fairly safe to override to 2.18.3
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.18.3"
)

// Exclude all transitive Akka dependencies
libraryDependencies ~= { deps =>
  deps.map(_.excludeAll(ExclusionRule(organization = "com.typesafe.akka")))
}

// identity-auth-core relies on `lift-json` which has not been updated to scala-xml 2.0.0
// Tell SBT to ignore the version conflict. This is fairly accepted practice for scala-xml: https://github.com/sbt/sbt/issues/6997
// Long term fix is that we release a new version of identity-auth-core that uses Json4s instead of lift-json
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

Compile / mainClass := Some("com.gu.adapters.http.JettyLauncher")

// package stuff - note, assumes presence of cfn and rr files
Universal / packageName := normalizedName.value
Universal / mappings ++= directory("conf")
scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(SpacesAroundMultiImports, false)
