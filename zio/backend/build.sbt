import Dependencies.Libraries

organization in ThisBuild := "some-org"
scalaVersion in ThisBuild := "2.13.1"

lazy val commonSettings = Seq(
  scalafmtOnCompile := true,
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  Test / fork := true,
  libraryDependencies ++= Seq(
    compilerPlugin(Libraries.kindProjector),
    compilerPlugin(Libraries.betterMonadicFor),
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    "Confluent Maven Repo" at "https://packages.confluent.io/maven/"
  )
)

lazy val integrationTestSettings = Defaults.itSettings ++ Seq(
  libraryDependencies ++= (Libraries.zioTest ++ Seq(
      Libraries.testContainers,
    )).map(_ % IntegrationTest),
  ) ++ inConfig(IntegrationTest)(Seq(
  dependencyClasspath := (dependencyClasspath in IntegrationTest).value ++ (exportedProducts in Test).value,
  fork := true,
  scalafmtOnCompile := true,
) ++ ScalafmtPlugin.scalafmtConfigSettings)

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
      Libraries.zio
    ) ++ Libraries.logging
      ++ Libraries.zioTest.map(_ % Test)
  )

lazy val `backend-service-app` = project
  .in(file("app"))
  .configs(IntegrationTest)
  .dependsOn(`backend-service-core` % "test->test;compile->compile")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(
    mainClass in reStart := Some("backend.Main"),
    libraryDependencies ++= Seq(
      Libraries.zioConfig,
      Libraries.zioCatsInterop,
      Libraries.fs2Kafka,
    )
  )
