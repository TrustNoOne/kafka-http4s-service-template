package backend.service

import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import pureconfig.ConfigSource
import backend.helloworld.HelloWorld

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = readConfig()

    val helloWorld = HelloWorld.impl[IO]
    KafkaGreeterService.impl[IO](config, helloWorld).start().as(ExitCode.Success)
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
