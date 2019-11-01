package web.app

import cats.effect.{ Clock, ExitCode, IO, IOApp }
import pureconfig.ConfigSource
import web.service.GreetingsRepo

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- IO(ConfigSource.default.loadOrThrow[Config])
      greetingsRepo <- GreetingsRepo.impl(size = 3, Clock.create[IO])
      listener <- GreetingsListener.impl(config.kafka, greetingsRepo).readGreetings().start

      _ <- Server.stream[IO](config.web, greetingsRepo)
      _ <- listener.cancel
    } yield ExitCode.Success

}
