addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0") 

addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "4.0.1")

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.0.0")