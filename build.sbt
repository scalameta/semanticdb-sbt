import scala.xml.{Node => XmlNode, NodeSeq => XmlNodeSeq, _}
import scala.xml.transform.{RewriteRule, RuleTransformer}

name := "sbthostRoot"
nonPublishableSettings
version in ThisBuild := customVersion.getOrElse(version.in(ThisBuild).value)
moduleName := "sbthostRoot"

lazy val nsc = project
  .in(file("sbthost/nsc"))
  .settings(
    publishableSettings,
    isFullCrossVersion,
    isScala210,
    moduleName := "semanticdb-sbt",
    mergeSettings,
    description := "Compiler plugin to produce .semanticdb files for sbt builds.",
    libraryDependencies ++= List(
      "org.scalameta" %% "langmeta" % scalametaVersion,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    )
  )

lazy val runtime = project
  .in(file("sbthost/runtime"))
  .settings(
    publishableSettings,
    moduleName := "semanticdb-sbt-runtime",
    libraryDependencies += "org.scalameta" %% "scalameta" % scalametaVersion,
    description := "Library to patch broken .semanticdb files produced by semanticdb-sbt."
  )

val sbtHostScalacOptions =
  settingKey[Seq[String]]("Scalac options required for the sbt host plugin.")
sbtHostScalacOptions.in(Global) := {
  val jarname = s"semanticdb-sbt_2.10.6-${version.value}.jar"
  // TODO(olafur) avoid getparent()
  val sbthostPlugin = classDirectory.in(nsc, Compile).value.getParentFile / jarname
  val sbthostPluginPath = sbthostPlugin.getAbsolutePath
  s"-Xplugin:$sbthostPluginPath" ::
    "-Xplugin-require:semanticdb-sbt" ::
    Nil
}

lazy val input = project
  .in(file("sbthost/input"))
  .settings(
    nonPublishableSettings,
    isScala210,
    sbtPlugin := true,
    compile.in(Compile) :=
      compile.in(Compile).dependsOn(Keys.`package`.in(nsc, Compile)).value,
    scalacOptions ++= sbtHostScalacOptions.value,
    scalacOptions += "-Jdummy=" + System.currentTimeMillis()
  )

lazy val sbtTests = project
  .in(file("sbthost/sbt-tests"))
  .settings(
    nonPublishableSettings,
    moduleName := "sbthost-sbt-tests",
    scalaVersion := scala210,
    description := "Tests for sbthost that check semantic generation for sbt files.",
    scriptedSettings,
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= {
      val targetDirectory: File = classDirectory.in(Compile).value
      val options: Seq[String] =
        s"-P:semanticdb-sbt:targetroot:$targetDirectory" +:
          sbtHostScalacOptions.value
      Seq(
        "-Xmx1024M",
        "-XX:MaxPermSize=256M",
        s"-Dsbthost.config=${options.mkString("\007")}"
      )
    },
    scripted := scripted.dependsOn(Keys.`package`.in(nsc, Compile)).evaluated
  )

lazy val tests = project
  .in(file("sbthost/tests"))
  .settings(
    nonPublishableSettings,
    moduleName := "sbthost-tests",
    scalaVersion := scala212,
    description := "Tests for sbthost",
    libraryDependencies += "org.scalameta" %% "testkit" % scalametaVersion % Test,
    compileInputs.in(Compile, compile) :=
      compileInputs.in(Compile, compile).dependsOn(compile.in(input, Compile)).value,
    test.in(Test) :=
      test
        .in(Test)
        .dependsOn(scripted.in(sbtTests).toTask(""))
        .dependsOn(Keys.`package`.in(nsc, Compile))
        .value,
    buildInfoPackage := "scala.meta.tests",
    buildInfoKeys := Seq[BuildInfoKey](
      "targetroot" -> classDirectory.in(input, Compile).value,
      "sourceroot" -> baseDirectory.in(ThisBuild).value,
      "sbtTargetroot" -> classDirectory.in(sbtTests, Compile).value,
      "sbtSourceroot" ->
        sourceDirectory.in(sbtTests).value / "sbt-test" / "migration" / "basic"
    )
  )
  .dependsOn(runtime)
  .enablePlugins(BuildInfoPlugin)

lazy val mergeSettings = Def.settings(
  test.in(assembly) := {},
  logLevel.in(assembly) := Level.Error,
  assemblyJarName.in(assembly) :=
    name.value + "_" + scalaVersion.value + "-" + version.value + "-assembly.jar",
  assemblyOption.in(assembly) ~= { _.copy(includeScala = false) },
  Keys.`package`.in(Compile) := {
    val slimJar = Keys.`package`.in(Compile).value
    val fatJar =
      new File(crossTarget.value + "/" + assemblyJarName.in(assembly).value)
    val _ = assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
    slimJar
  },
  packagedArtifact.in(Compile).in(packageBin) := {
    val temp = packagedArtifact.in(Compile).in(packageBin).value
    val (art, slimJar) = temp
    val fatJar =
      new File(crossTarget.value + "/" + assemblyJarName.in(assembly).value)
    val _ = assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
    (art, slimJar)
  },
  pomPostProcess := { node =>
    new RuleTransformer(new RewriteRule {
      private def isScalametaDependency(node: XmlNode): Boolean = {
        def isArtifactId(node: XmlNode, fn: String => Boolean) =
          node.label == "artifactId" && fn(node.text)
        node.label == "dependency" && node.child.exists(child =>
          isArtifactId(child, _.startsWith("scalameta_")))
      }
      override def transform(node: XmlNode): XmlNodeSeq = node match {
        case e: Elem if isScalametaDependency(node) =>
          Comment("scalameta dependency has been merged into sbthost via sbt-assembly")
        case _ => node
      }
    }).transform(node).head
  }
)

// NOTE: Can't upgrade to 2.10.7, because sbt is using 2.10.6.
// (Well, it WAS using 2.10.6 until it was upgraded to 2.10.7 in 0.13.17).
lazy val scalametaVersion = "3.2.0"
lazy val scala210 = "2.10.6"
lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.4"

lazy val isScala210 = Seq(
  scalaVersion := scala210,
  crossScalaVersions := List(scala210)
)

lazy val sharedSettings: Seq[Def.Setting[_]] = Seq(
  scalaVersion := scala212,
  crossScalaVersions := List(scala212, scala211),
  organization := "org.scalameta",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  updateOptions := updateOptions.value.withCachedResolution(true),
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("releases"),
  triggeredMessage.in(ThisBuild) := Watched.clearWhenTriggered
)

lazy val nonPublishableSettings: Seq[Def.Setting[_]] = Seq(
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in packageDoc := false,
  sources in (Compile, doc) := Seq.empty,
  publishArtifact := false,
  publish := {}
) ++ sharedSettings

lazy val publishableSettings: Seq[Def.Setting[_]] = Seq(
  publishTo := {
    if (customVersion.isDefined)
      Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    else publishTo.in(bintray).value
  },
  bintrayOrganization := Some("scalameta"),
  bintrayRepository := "maven",
  publishMavenStyle := true,
  licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
  pomExtra := (
    <url>https://github.com/scalameta/scalameta</url>
    <inceptionYear>2017</inceptionYear>
    <scm>
      <url>git://github.com/scalameta/scalameta.git</url>
      <connection>scm:git:git://github.com/scalameta/scalameta.git</connection>
    </scm>
    <issueManagement>
      <system>GitHub</system>
      <url>https://github.com/scalameta/scalameta/issues</url>
    </issueManagement>
    <developers>
      <developer>
        <id>olafurpg</id>
        <name>Ólafur Páll Geirsson</name>
        <url>https://geirsson.com/</url>
      </developer>
    </developers>
  )
) ++ sharedSettings

lazy val isFullCrossVersion = Seq(
  crossVersion := CrossVersion.full,
  unmanagedSourceDirectories.in(Compile) += {
    // NOTE: sbt 0.13.8 provides cross-version support for Scala sources
    // (http://www.scala-sbt.org/0.13/docs/sbt-0.13-Tech-Previews.html#Cross-version+support+for+Scala+sources).
    // Unfortunately, it only includes directories like "scala_2.11" or "scala_2.12",
    // not "scala_2.11.8" or "scala_2.12.1" that we need.
    // That's why we have to work around here.
    val base = sourceDirectory.in(Compile).value
    base / ("scala-" + scalaVersion.value)
  }
)

commands += Command.command("release") { s =>
  "clean" ::
    "very publishSigned" ::
    "sonatypeRelease" ::
    "gitPushTag" ::
    s
}

lazy val gitPushTag = taskKey[Unit]("Push to git tag")
gitPushTag := {
  val tag = s"v${version.value}"
  assert(!tag.endsWith("SNAPSHOT"))
  import sys.process._
  Seq("git", "tag", "-a", tag, "-m", tag).!!
  Seq("git", "push", "--tags").!!
}

lazy val customVersion = sys.props.get("sbthost.version")
