import sbt._

object Dependencies {

  object Versions {
    val cats       = "2.0.0"
    val catsEffect = "2.0.0"
    val fs2Kafka   = "0.20.1"
    val pureConfig = "0.12.1"

    val log4j2 = "2.12.1"

    val http4s = "0.21.0-M5"
    val circe = "0.12.3"
    val circeDerivation = "0.12.0-M7"

    // Test
    val miniTest  = "2.7.0"

    // Integration Test
    val dockerTestkit = "0.9.9"

    // Compiler
    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.1"
  }

  object Libraries {
    lazy val cats       = "org.typelevel" %% "cats-core"   % Versions.cats
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2Kafka   = "com.ovoenergy" %% "fs2-kafka"   % Versions.fs2Kafka
    lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig

    lazy val log4j2 = Seq(
      "org.apache.logging.log4j" % "log4j-api",
      "org.apache.logging.log4j" % "log4j-core",
      "org.apache.logging.log4j" % "log4j-slf4j-impl",
      "org.apache.logging.log4j" % "log4j-1.2-api",
      "org.apache.logging.log4j" % "log4j-jul",
    ).map(_ % Versions.log4j2)

    lazy val http4s = Seq(
      "org.http4s"      %% "http4s-blaze-server" % Versions.http4s,
      "org.http4s"      %% "http4s-blaze-client" % Versions.http4s,
      "org.http4s"      %% "http4s-circe"        % Versions.http4s,
      "org.http4s"      %% "http4s-dsl"          % Versions.http4s,
    )

    lazy val circeDerivation = "io.circe" %% "circe-derivation" % Versions.circeDerivation
    lazy val circeParser     = "io.circe" %% "circe-parser"     % Versions.circe

    // Test
    lazy val miniTest  = "io.monix" %% "minitest-laws" % Versions.miniTest
    lazy val catsLaws  = "org.typelevel" %% "cats-laws" % Versions.cats

    // Integration Test
    lazy val dockerTestKit = Seq(
      "com.whisk" %% "docker-testkit-core" % Versions.dockerTestkit,
      "com.whisk" %% "docker-testkit-impl-spotify" % Versions.dockerTestkit,
      "javax.activation" % "activation" % "1.1.1"
    )

    // Compiler
    lazy val kindProjector    = "org.typelevel" %% "kind-projector"     % Versions.kindProjector
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % Versions.betterMonadicFor
  }

}
