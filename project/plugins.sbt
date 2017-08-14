addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version)
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "1.2.0")
addSbtPlugin("com.eed3si9n" % "sbt-doge" % "0.1.5")
// exclude is a workaround for https://github.com/sbt/sbt-assembly/issues/236#issuecomment-294452474
addSbtPlugin(
  "com.eed3si9n" % "sbt-assembly" % "0.14.5" exclude ("org.apache.maven", "maven-plugin-api"))
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin(
  "com.thesamet" % "sbt-protoc" % "0.99.6" exclude ("com.trueaccord.scalapb", "protoc-bridge_2.10"))
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin-shaded" % "0.6.0"
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
