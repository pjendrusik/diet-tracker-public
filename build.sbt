import Dependencies._

ThisBuild / organization := "com.diettracker"
ThisBuild / scalaVersion := scala
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalafmtOnCompile := true

lazy val root = (project in file("."))
  .settings(
    name := "diet-tracker",
    libraryDependencies ++= coreDependencies,
    Test / fork := true,
    Test / parallelExecution := false,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Compile / run / mainClass := Some("com.diettracker.Main"),
    Compile / console / scalacOptions --= Seq("-Xfatal-warnings")
  )

addCommandAlias("fmt", "scalafmtAll")
addCommandAlias("fmtCheck", "scalafmtCheckAll")
addCommandAlias("ci", ";clean;fmtCheck;Test/compile;test")
