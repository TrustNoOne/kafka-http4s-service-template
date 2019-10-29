import Dependencies.Libraries

name := """kafka-http4s-service-template"""

organization in ThisBuild := "some-org"
scalaVersion in ThisBuild := "2.13.1"

lazy val commonSettings = Seq(
  scalafmtOnCompile := true,
  testFrameworks += new TestFramework("minitest.runner.Framework"),
  Test / fork := true,
  libraryDependencies ++= Seq(
    Libraries.cats,
    Libraries.catsEffect,
    Libraries.catsLaws % Test,
    Libraries.miniTest % Test,
    compilerPlugin(Libraries.kindProjector),
    compilerPlugin(Libraries.betterMonadicFor)
  ) ++ Libraries.logging,
)

lazy val integrationTestSettings = Defaults.itSettings ++ Seq(
  libraryDependencies ++= Seq(
    Libraries.miniTest,
    Libraries.testContainers
  ).map(_ % IntegrationTest),
) ++ inConfig(IntegrationTest)(Seq(
  fork := true,
  scalafmtOnCompile := true,
))

lazy val `kafka-http4s-template-root` = project
    .in(file("."))
    .aggregate(
      `kafka-http4s-template-core`,
      `kafka-http4s-template-app`,
    )

lazy val `kafka-http4s-template-core` = project
  .in(file("core"))
  .settings(commonSettings: _*)

lazy val `kafka-http4s-template-app` = project
  .in(file("app"))
  .configs(IntegrationTest)
  .dependsOn(`kafka-http4s-template-core` % "test->test;compile->compile")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(
    mainClass in reStart := Some("servicetmpl.app.Main"),
    libraryDependencies ++= Seq(
      Libraries.circeDerivation,
      Libraries.circeParser,
      Libraries.fs2Kafka,
      Libraries.pureConfig,
    ) ++ Libraries.http4s
  )
