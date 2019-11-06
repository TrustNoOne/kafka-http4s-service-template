import Dependencies.Libraries

organization in ThisBuild := "some-org"
scalaVersion in ThisBuild := "2.13.1"

lazy val commonSettings = Seq(
  scalafmtOnCompile := true,
  testFrameworks += new TestFramework("minitest.runner.Framework"),
  Test / fork := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    "Confluent Maven Repo" at "https://packages.confluent.io/maven/"
  )
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


lazy val `web-service` = project
    .in(file("."))
    .aggregate(
      `web-service-core`,
      `web-service-app`,
    )

lazy val `web-service-core` = project
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

lazy val `web-service-app` = project
  .in(file("app"))
  .configs(IntegrationTest)
  .dependsOn(`web-service-core` % "test->test;compile->compile")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(
    mainClass in reStart := Some("web.app.Main"),
    libraryDependencies ++= Seq(
      Libraries.circeDerivation,
      Libraries.circeParser,
      Libraries.fs2Kafka,
      Libraries.pureConfig,
    ) ++ Libraries.http4s
  )
