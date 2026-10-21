addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
// scalastyle still pulls in scalariform, which depends on an older scala-xml.
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")

addDependencyTreePlugin
