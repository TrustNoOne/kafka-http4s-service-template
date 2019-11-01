import sbt._

object Dependencies {

  private[Dependencies] object Versions {
    val cats       = "2.0.0"
    val catsEffect = "2.0.0"
    val fs2Kafka   = "0.20.2"
    val pureConfig = "0.12.1"

    val log4j2 = "2.12.1"
    val log4s = "1.8.2"

    val circe = "0.12.3"
    val circeDerivation = "0.12.0-M7"

    // Test
    val miniTest  = "2.7.0"

    // Integration Test
    val testContainers = "0.33.0"

    // Compiler
    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.1"
  }

  object Libraries {
    lazy val cats       = "org.typelevel" %% "cats-core"   % Versions.cats
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2Kafka   = "com.ovoenergy" %% "fs2-kafka"   % Versions.fs2Kafka
    lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig

    lazy val logging = Seq(
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % Versions.log4j2,
      "org.log4s"               %% "log4s"            % Versions.log4s
    )

    lazy val circeDerivation = "io.circe" %% "circe-derivation" % Versions.circeDerivation
    lazy val circeParser     = "io.circe" %% "circe-parser"     % Versions.circe

    // Test
    lazy val miniTest  = "io.monix" %% "minitest-laws" % Versions.miniTest
    lazy val catsLaws  = "org.typelevel" %% "cats-laws" % Versions.cats

    // Integration Test
    lazy val testContainers ="com.dimafeng" %% "testcontainers-scala" % Versions.testContainers

    // Compiler
    lazy val kindProjector    = "org.typelevel" %% "kind-projector"     % Versions.kindProjector
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Versions.betterMonadicFor
  }

}
