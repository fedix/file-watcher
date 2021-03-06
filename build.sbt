ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

Compile / run / fork := true

val fs2Version = "3.2.4"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect"     % "3.3.5",
  "co.fs2"        %% "fs2-core"        % fs2Version,
  "co.fs2"        %% "fs2-io"          % fs2Version,
  "org.typelevel" %% "log4cats-slf4j"  % "2.2.0",
  "ch.qos.logback" % "logback-classic" % "1.2.10"
)

lazy val root = (project in file("."))
  .settings(name := "file-watcher")

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
