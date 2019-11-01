import Dependencies.Libraries

organization in ThisBuild := "some-org"
scalaVersion in ThisBuild := "2.13.1"

lazy val commonSettings = Seq(
  scalafmtOnCompile := true,
  testFrameworks += new TestFramework("minitest.runner.Framework"),
  Test / fork := true,
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

lazy val `backend-service` = project
    .in(file("."))
    .aggregate(
      `backend-service-core`,
      `backend-service-app`,
    )

lazy val `backend-service-core` = project
  .in(file("core"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Libraries.cats,
      Libraries.catsEffect,
      Libraries.catsLaws % Test,
      Libraries.miniTest % Test,
      compilerPlugin(Libraries.kindProjector),
      compilerPlugin(Libraries.betterMonadicFor)
    ) ++ Libraries.logging
  )

lazy val `backend-service-app` = project
  .in(file("app"))
  .configs(IntegrationTest)
  .dependsOn(`backend-service-core` % "test->test;compile->compile")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(
    mainClass in reStart := Some("backend.service.Main"),
    libraryDependencies ++= Seq(
      Libraries.circeDerivation,
      Libraries.circeParser,
      Libraries.fs2Kafka,
      Libraries.pureConfig,
    )
  )
