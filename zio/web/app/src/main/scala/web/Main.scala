package web

import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import web.kafka.KafkaProducer
import web.module.HelloRequester.HelloRequested
import web.module.{ GreetingsListener, GreetingsRepo, HelloRequester }
import zio._
import zio.clock.Clock
import zio.config.{ config, Config }
import zio.interop.catz.{ console => _, _ }
import zio.macros.delegate._
import zio.macros.delegate.syntax._
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

object Main extends ManagedApp {

  override def run(args: List[String]): ZManaged[ZEnv, Nothing, Int] = {
    type Env = Clock
      with Config[AppConfig]
      with Logging[String]
      with GreetingsListener
      with GreetingsRepo
      with HelloRequester

    def runServer: ZIO[Env, Throwable, Unit] =
      ZIO
        .runtime[Env]
        .flatMap { implicit rt =>
          for {
            _ <- logger.info("Starting web app...")

            listener <- GreetingsListener.>.readGreetings.fork

            httpApp = Router[RIO[Env, *]](
              "/" -> Routes.appRoutes,
              "/docs" -> Routes.openApiRoutes
            ).orNotFound

            loggedHttpApp = Logger.httpApp[RIO[Env, *]](
              logHeaders = true,
              logBody = true,
              logAction = Some(s => logger.debug(s))
            )(httpApp)

            conf <- config[AppConfig]
            _ <- BlazeServerBuilder[RIO[Env, *]]
                  .bindHttp(conf.web.listenPort, conf.web.listenHost)
                  .withHttpApp(loggedHttpApp)
                  .serve
                  .compile
                  .drain

            _ <- listener.interrupt
          } yield ()
        }

    (ZIO.environment[ZEnv] @@
      enrichWithM(Config.fromEnv(AppConfig.decription)) @@
      enrichWithM(AppLogging.env) @@
      enrichWithM(GreetingsRepo.Live.make(3)) @@
      enrichWithM(GreetingsListener.Live.make) @@
      enrichWithManaged(
        KafkaProducer.make[Unit, HelloRequested].mapM(HelloRequester.Live.make)
      ) >>> runServer.toManaged_)
      .foldM(
        fail => console.putStrLn(s"Initialization Failed $fail").as(1).toManaged_,
        _ => ZManaged.succeed(0)
      )
  }
}
