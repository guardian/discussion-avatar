addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0") 

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")
// sbt-scalariform has not updated to scala-xml 2.0.0 yet.
// Tell SBT to ignore the version conflict. This is fairly accepted practice for scala-xml: https://github.com/sbt/sbt/issues/6997
// Long term fix is that we should switch to sbt-scalafmt
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")

addDependencyTreePlugin
