package web.app

import cats.effect.{ Clock, ExitCode, IO, IOApp }
import pureconfig.ConfigSource
import web.service.GreetingsRepo

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    IO(ConfigSource.default.loadOrThrow[Config]) flatMap { config =>
      HelloRequester
        .impl[IO](config.kafka, config.helloWorld.requestsTopic)
        .use { helloRequester =>
          for {
            greetingsRepo <- GreetingsRepo.impl(size = 3, Clock.create[IO])
            greetingsListener = GreetingsListener.impl(config.kafka, config.helloWorld.greetingsTopic, greetingsRepo)
            listener <- greetingsListener.readGreetings().start

            _ <- Server.stream[IO](config.web, greetingsRepo, helloRequester)
            _ <- listener.cancel
          } yield ExitCode.Success
        }
    }
}
