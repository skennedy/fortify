ThisBuild / organization := "com.github.skennedy"
ThisBuild / scalaVersion := "3.2.1"

val http4sVersion = "0.23.18"

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "fortify",
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "epollcat"            % "0.1.1", // Runtime
      "com.monovore"   %%% "decline"             % "2.4.1",
      "com.monovore"   %%% "decline-effect"      % "2.4.1",
      "org.typelevel"  %%% "cats-effect"         % "3.4.6",
      "io.circe"       %%% "circe-generic"       % "0.14.3",
      "org.http4s"     %%% "http4s-circe"        % http4sVersion,
      "org.http4s"     %%% "http4s-dsl"          % http4sVersion,
      "org.http4s"     %%% "http4s-ember-client" % http4sVersion,
      "org.http4s"     %%% "http4s-ember-server" % http4sVersion
    ),
    Compile / mainClass           := Some("com.github.skennedy.fortify.Main"),
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )
