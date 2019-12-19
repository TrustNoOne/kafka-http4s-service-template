package web.app

import cats.effect.{ ConcurrentEffect, ContextShift, Timer }
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import web.service.GreetingsRepo
import web.utils.Logging

object Server extends Logging {

  def stream[F[_]: ConcurrentEffect: Timer: ContextShift](
      webConfig: WebConfig,
      greetings: GreetingsRepo[F],
      helloRequester: HelloRequester[F]
  ): F[Unit] = {
    val logger = getLogger[F]

    val httpApp = Router(
      "/" -> Routes.helloWorldRoutes[F](greetings, helloRequester),
      "/docs" -> Routes.openApiRoutes[F]
    ).orNotFound

    val loggedHttpApp = Logger.httpApp(
      logHeaders = true,
      logBody = true,
      logAction = Some { s: String =>
        logger.debug(s)
      }
    )(httpApp)

    BlazeServerBuilder[F]
      .bindHttp(webConfig.listenPort, webConfig.listenHost)
      .withHttpApp(loggedHttpApp)
      .serve

  }.compile.drain
}
