ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "zio-api-server-example"
  )
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.1",
      "dev.zio" %% "zio-json" % "0.3.0-RC11",
      "io.d11" %% "zhttp" % "2.0.0-RC10"
    )
  )
