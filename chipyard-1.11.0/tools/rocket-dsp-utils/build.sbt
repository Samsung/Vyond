// SPDX-License-Identifier: Apache-2.0

git.remoteRepo := "git@github.com:ucb-bar/dsptools.git"

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

name := "rocket-dsp-utils"

val chiselVersion = "3.6.0"

lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chiselVersion,
    "org.apache.commons" % "commons-lang3" % "3.12.0",
    "org.apache.commons" % "commons-text" % "1.9"),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full))

val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "0.6-SNAPSHOT",
  autoAPIMappings := true,
  scalaVersion := "2.13.10",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls", "-Ymacro-annotations", "-Xsource:2.13"),
//  javacOptions ++= javacOptionsVersion(scalaVersion.value),
  pomExtra := (<url>http://chisel.eecs.berkeley.edu/</url>
  <licenses>
    <license>
      <name>apache_v2</name>
      <url>https://opensource.org/licenses/Apache-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>grebe</id>
      <name>Paul Rigge</name>
      <url>http://www.eecs.berkeley.edu/~rigge/</url>
    </developer>
    <developer>
      <id>shunshou</id>
      <name>Angie Wang</name>
      <url>https://www.linkedin.com/in/angie-wang-ee/</url>
    </developer>
    <developer>
      <id>chick</id>
      <name>Charles Markley</name>
      <url>https://aspire.eecs.berkeley.edu/author/chick/</url>
    </developer>
   </developers>),
  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) {
      Some("snapshots" at nexus + "content/repositories/snapshots")
    }
    else {
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  },
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  // scala-steward:on
  libraryDependencies ++= Seq(
    "org.typelevel" %% "spire" % "0.18.0",
    "org.scalanlp" %% "breeze" % "2.1.0",
    "org.scalatest" %% "scalatest" % "3.2.15" % "test",
  ),
)

val rocketSettings = Seq(
    name := "rocket-dsp-utils",
    Test / parallelExecution := false,
)

lazy val managedDepSettings = Seq(
  scalacOptions += "-Wconf:any:is"
)

publishMavenStyle := true

Test / publishArtifact := false
pomIncludeRepository := { x => false }

/**
 * It has been a struggle for us to override settings in subprojects.
 * An example would be adding a dependency to rocketchip on midas's targetutils library,
 * or replacing dsptools's maven dependency on chisel with the local chisel project.
 *
 * This function works around this by specifying the project's root at src/ and overriding
 * scalaSource and resourceDirectory.
 */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      Compile / scalaSource := baseDirectory.value / "main" / "scala",
      Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
    )
}

// Subproject definitions begin

// -- Rocket Chip --

val rocketChipDir = file("tools/rocket-chip")

// Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = freshProject("hardfloat", rocketChipDir / "hardfloat")
  .settings(chiselSettings)
  .settings(commonSettings)
  .settings(managedDepSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)
  .settings(managedDepSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    )
  )

lazy val cde = (project in rocketChipDir / "cde")
  .settings(commonSettings)
  .settings(managedDepSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "cde/src/chipsalliance/rocketchip")

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .dependsOn(hardfloat, rocketMacros, cde)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(managedDepSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.6",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test",
      "org.scala-graph" %% "graph-core" % "1.13.5"
    )
  )
  .settings( // Settings for scalafix
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
  )
lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)


// -- DSP Tools --

lazy val dsptoolsDir = file("tools/dsptools")

lazy val fixedpoint = freshProject("fixedpoint", dsptoolsDir / "fixedpoint")
  .settings(
    chiselSettings,
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % "test",
      "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
    )
  )

lazy val dsptools = freshProject("dsptools", dsptoolsDir)
  .dependsOn(fixedpoint)
  .settings(
    chiselSettings,
    commonSettings,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % "test",
      "org.typelevel" %% "spire" % "0.18.0",
      "org.scalanlp" %% "breeze" % "2.1",
      "junit" % "junit" % "4.13.2" % "test",
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0",
      "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
    ))


val `rocket-dsp-utils` = (project in file("."))
  .dependsOn(rocketchip, cde, dsptools, fixedpoint)
  .settings(
    chiselSettings,
    commonSettings,
    libraryDependencies ++= rocketLibDeps.value,
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0",
    )
  )
