package web

import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import web.module.HelloRequester.HelloRequested
import web.module.{ GreetingsListener, GreetingsRepo, HelloRequester, KafkaProducerBuilder, StoredGreeting }
import zio._
import zio.clock.Clock
import zio.config.{ config, Config }
import zio.console.Console
import zio.interop.catz.{ console => _, _ }
import zio.logging._
import zio.logging.slf4j._

object AppLogging {
  val correlationId: ContextKey[String] =
    ContextKey[String]("correlationId", "main")

  val env: UIO[Logging[String] with LoggingContext] =
    ContextMap.empty.map { ctxMap =>
      val stringFormat = "[%s] %s"

      new Slf4jLogger.Live with LoggingContext {
        self =>
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
  val env =
    for {
      configEnv <- ZManaged.fromEffect(Config.fromEnv(AppConfig.decription))
      clockEnv <- ZManaged.fromEffect(clock.clockService)
      consoleEnv <- ZManaged.fromEffect(console.consoleService)
      loggingEnv <- ZManaged.fromEffect(AppLogging.env)
      repoBuffer <- ZManaged.fromEffect(Ref.make(Seq.empty[StoredGreeting]))
      kafkaHelloReqProd <- KafkaProducerBuilder.Live.kafkaProducerBuilder
                            .getKafkaProducer[Unit, HelloRequested]
                            .provide(configEnv)
    } yield new Config[AppConfig]
      with Console
      with Clock
      with Logging[String]
      with LoggingContext
      with GreetingsRepo.Live
      with GreetingsListener.Live
      with HelloRequester.Live {
      // Provided by ZEnv
      val clock   = clockEnv
      val console = consoleEnv

      // Other effectful dependencies
      val loggingContext = loggingEnv.loggingContext
      val logging        = loggingEnv.logging
      val config         = configEnv.config

      override val liveGreetingsRepoBufferSize = 3
      override val liveGreetingsRepoBufferRef  = repoBuffer
      override val kafkaHelloRequestedProducer = kafkaHelloReqProd
    }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    import Routes.Env
    val httpServer: ZIO[Env, Throwable, Unit] = ZIO.runtime[Env].flatMap { implicit rt =>
      for {
        conf <- config[AppConfig]

        httpApp = Router[RIO[Env, *]](
          "/" -> Routes.appRoutes,
          "/docs" -> Routes.openApiRoutes
        ).orNotFound

        loggedHttpApp = Logger.httpApp[RIO[Env, *]](
          logHeaders = true,
          logBody = true,
          logAction = Some(s => logger.debug(s))
        )(httpApp)

        server <- BlazeServerBuilder[RIO[Env, *]]
                   .bindHttp(conf.web.listenPort, conf.web.listenHost)
                   .withHttpApp(loggedHttpApp)
                   .serve
                   .compile
                   .drain

      } yield server
    }

    val program =
      for {
        _ <- logger.info("Starting web app...")
        listener <- GreetingsListener.>.readGreetings.fork

        _ <- httpServer
        _ <- listener.interrupt
      } yield ()

    program
      .provideSomeManaged(env)
      .foldM(
        fail => zio.console.putStrLn(s"Initialization Failed $fail") *> ZIO.succeed(1),
        _ => ZIO.succeed(0)
      )
  }
}
