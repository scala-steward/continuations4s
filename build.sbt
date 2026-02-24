import java.util.Properties
import xerial.sbt.Sonatype.sonatypeCentralHost

val publishLocalGradleDependencies =
  taskKey[Unit]("Builds and publishes gradle dependencies")

val props =
  settingKey[Properties]("Main project properties")

inThisBuild(
  Seq(
    organization := "org.funfix",
    scalaVersion := "3.8.1",
    // Configure for Sonatype Central Portal
    sonatypeCredentialHost := sonatypeCentralHost,
    usePgpKeyHex(sys.env.getOrElse("PGP_KEY_ID", "")),
    // ---
    // Settings for dealing with the local Gradle-assembled artifacts
    // Also see: publishLocalGradleDependencies
    resolvers ++= Seq(Resolver.mavenLocal),
    version := "0.0.1-SNAPSHOT",
    versionScheme := Some("early-semver")
  )
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val sharedSettings = Seq(
  scalacOptions ++=
    Seq("-no-indent", "-rewrite"),
  // Compile / compile / wartremoverErrors ++= Seq(
  //   Wart.AsInstanceOf,
  //   Wart.ExplicitImplicitTypes,
  //   Wart.FinalCaseClass,
  //   Wart.FinalVal,
  //   Wart.ImplicitConversion,
  //   Wart.IsInstanceOf,
  //   Wart.JavaSerializable,
  //   Wart.LeakingSealed,
  //   Wart.NonUnitStatements,
  //   Wart.TripleQuestionMark,
  //   Wart.TryPartial,
  //   Wart.Return,
  //   Wart.PublicInference,
  //   Wart.OptionPartial,
  //   Wart.ArrayEquals
  // ),
  organization := "org.funfix",
  organizationName := "Funfix",
  organizationHomepage := Some(url("https://funfix.org")),

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/funfix/continuations4s"),
      "scm:git@github.com:funfix/continuations4s.git"
    )
  ),
  developers := List(
    Developer(
      id = "alexelcu",
      name = "Alexandru Nedelcu",
      email = "noreply@alexn.org",
      url = url("https://alexn.org")
    )
  ),

  description :=
    "Exposes a lower-level continuations API, supported on JVM, WASM, and Native platforms.",
  licenses := List(License.Apache2),
  homepage := Some(url("https://github.com/funfix/continuations4s")),

  // Remove all additional repository other than Maven Central from POM
  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,

  // new setting for the Central Portal
  publishTo := {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (version.value.endsWith("-SNAPSHOT")) Some("central-snapshots".at(centralSnapshots))
    else sonatypePublishToBundle.value
  },

  // ScalaDoc settings
  autoAPIMappings := true,
  scalacOptions ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath",
    file(".").getAbsolutePath.replaceAll("[.]$", ""),
    // Debug warnings
    "-Wconf:any:warning-verbose"
  )
)

lazy val continuations4s = crossProject( /*JSPlatform, JVMPlatform,*/ NativePlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(sharedSettings)
  .settings(
    name :=
      "continuations4s"
      // libraryDependencies ++= Seq(
      //   "org.funfix" % "delayedqueue-jvm" % version.value,
      //   "org.typelevel" %% "cats-effect" % "3.6.3",
      //   // Testing
      //   "org.scalameta" %% "munit" % "1.0.4" % Test,
      //   "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
      //   "org.typelevel" %% "cats-effect-testkit" % "3.6.3" % Test,
      //   "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
      //   "org.scalameta" %% "munit-scalacheck" % "1.2.0" % Test,
      //   // JDBC drivers for testing
      //   "com.h2database" % "h2" % "2.4.240" % Test,
      //   "org.hsqldb" % "hsqldb" % "2.7.4" % Test,
      //   "org.xerial" % "sqlite-jdbc" % "3.51.1.0" % Test
      // )
  )

addCommandAlias(
  "ci-test",
  ";publishLocalGradleDependencies;+test;scalafmtCheckAll"
)
addCommandAlias(
  "ci-publish-local",
  ";publishLocalGradleDependencies; +Test/compile; +publishLocal"
)
addCommandAlias(
  "ci-publish",
  ";publishLocalGradleDependencies; +Test/compile; +publishSigned; sonatypeBundleRelease"
)
