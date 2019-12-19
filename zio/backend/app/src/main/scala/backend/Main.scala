package backend

import backend.module.helloworld.HelloWorld
import backend.service.KafkaGreeter
import zio._
import zio.clock.Clock
import zio.config.Config
import zio.logging.slf4j._
import zio.logging.{ Logging, _ }

object AppLogging {
  val correlationId: ContextKey[String] =
    ContextKey[String]("correlationId", "main")

  val env: UIO[Logging[String] with LoggingContext] =
    ContextMap.empty.map { ctxMap =>
      val stringFormat = "[%s] %s"

      new Slf4jLogger.Live with LoggingContext { self =>
        override def formatMessage(message: String): ZIO[Any, Nothing, String] =
          loggerContext
            .get(correlationId)
            .map(correlationId => stringFormat.format(correlationId, message))
            .provide(self)

        override def loggingContext: LoggingContext.Service[Any] = ctxMap
      }
    }
}

object Main extends App {

  val env = for {
    clockEnv <- clock.clockService
    consoleEnv <- console.consoleService
    loggingEnv <- AppLogging.env
    configEnv <- Config.fromEnv(AppConfig.decription)
  } yield new Config[AppConfig]
    with console.Console
    with Clock
    with Logging[String]
    with LoggingContext
    with HelloWorld.Live
    with KafkaGreeter.Live {
    // Provided by ZEnv
    val clock   = clockEnv
    val console = consoleEnv
    // Other effectful dependencies
    val loggingContext = loggingEnv.loggingContext
    val logging        = loggingEnv.logging
    val config         = configEnv.config
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val program = for {
      _ <- logger.info("Starting backend app...")
      _ <- service.start()
    } yield ()

    program
      .provideSomeM(env)
      .foldM(
        fail => zio.console.putStrLn(s"Initialization Failed $fail") *> ZIO.succeed(1),
        _ => ZIO.succeed(0)
      )
  }
}
