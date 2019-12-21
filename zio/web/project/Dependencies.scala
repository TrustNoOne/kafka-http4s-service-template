import sbt._

object Dependencies {

  private[Dependencies] object Versions {
    val zio = "1.0.0-RC17"
    val zioConfig = "1.0.0-RC7"
    val zioLogging = "0.0.4"
    val zioMacros = "0.6.2"
    val zioCatsInterop = "2.0.0.0-RC10"
    val fs2Kafka   = "0.20.2"

    val log4j2 = "2.13.0"

    val http4s = "0.21.0-M5"
    val tapir = "0.11.9"
    val circe = "0.12.3"
    val circeDerivation = "0.12.0-M7"

     // Integration Test
     val testContainers = "0.34.1"

    // Compiler
    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.1"
  }

  object Libraries {
    lazy val zio = "dev.zio" %% "zio-streams" % Versions.zio
    lazy val zioConfig = "dev.zio" %% "zio-config" % Versions.zioConfig
    lazy val zioCatsInterop = "dev.zio" %% "zio-interop-cats" % Versions.zioCatsInterop
    lazy val zioMacros = "dev.zio" %% "zio-macros-test" % Versions.zioMacros

    lazy val fs2Kafka  = "com.ovoenergy" %% "fs2-kafka-vulcan"  % Versions.fs2Kafka

    lazy val logging = Seq(
      "dev.zio" %% "zio-logging-slf4j" % Versions.zioLogging,
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % Versions.log4j2,
    )

     lazy val http4s = Seq(
       "org.http4s"      %% "http4s-blaze-server" % Versions.http4s,
       "org.http4s"      %% "http4s-blaze-client" % Versions.http4s,
       "org.http4s"      %% "http4s-circe"        % Versions.http4s,
       "org.http4s"      %% "http4s-dsl"          % Versions.http4s,
     )

     lazy val tapir = Seq(
       "com.softwaremill.tapir" %% "tapir-json-circe"         % Versions.tapir,
       "com.softwaremill.tapir" %% "tapir-http4s-server"      % Versions.tapir,
       "com.softwaremill.tapir" %% "tapir-openapi-docs"       % Versions.tapir,
       "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % Versions.tapir,
       "com.softwaremill.tapir" %% "tapir-swagger-ui-http4s"  % Versions.tapir,
     )

     lazy val circeDerivation = "io.circe" %% "circe-derivation" % Versions.circeDerivation
     lazy val circeParser     = "io.circe" %% "circe-parser"     % Versions.circe

    // Test
    lazy val zioTest = Seq(
      "dev.zio" %% "zio-test"     % Versions.zio,
      "dev.zio" %% "zio-test-sbt" % Versions.zio,
    )

    // Integration Test
    lazy val testContainers ="com.dimafeng" %% "testcontainers-scala" % Versions.testContainers

    // Compiler
    lazy val kindProjector    = "org.typelevel" %% "kind-projector"     % Versions.kindProjector
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Versions.betterMonadicFor
  }

}
