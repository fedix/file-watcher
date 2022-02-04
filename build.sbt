import sbt.Keys.libraryDependencies

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.3.4",
  "co.fs2"        %% "fs2-core"    % "3.2.4",
  "co.fs2"        %% "fs2-io"      % "3.2.4"
)

lazy val root = (project in file("."))
  .settings(name := "file-watcher")
