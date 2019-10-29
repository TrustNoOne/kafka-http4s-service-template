package servicetmpl.app

import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import pureconfig.ConfigSource
import servicetmpl.app.kafka.KafkaHelloWorld
import servicetmpl.app.web.Server
import servicetmpl.helloworld.{ HelloWorld, HelloWorldRepo }

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = readConfig()

    val helloWorldRepoF = HelloWorldRepo.impl(HelloWorld.impl[IO], size = 3)

    helloWorldRepoF.flatMap { helloWorldRepo =>
      List(
        Server.stream[IO](helloWorldRepo),
        KafkaHelloWorld.impl[IO](config.kafka, helloWorldRepo).start()
      ).parSequence
        .as(ExitCode.Success)
    }

  }

  def readConfig(): Config =
    // exit on error
    ConfigSource.default.load[Config] match {
      case Right(config) => config

      case Left(errors) =>
        System.err.println("Invalid Configuration: ")
        errors.toList foreach println
        System.exit(1)
        throw new Exception()
    }
}
