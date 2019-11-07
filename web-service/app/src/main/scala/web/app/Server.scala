package web.app

import cats.effect.{ ConcurrentEffect, Timer }
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import web.service.GreetingsRepo

object Server {

  def stream[F[_]: ConcurrentEffect: Timer](
      webConfig: WebConfig,
      greetings: GreetingsRepo[F],
      helloRequester: HelloRequester[F]
  ): F[Unit] = {
    val httpApp = Routes.helloWorldRoutes[F](greetings, helloRequester).orNotFound

    val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

    BlazeServerBuilder[F]
      .bindHttp(webConfig.listenPort, webConfig.listenHost)
      .withHttpApp(finalHttpApp)
      .serve

  }.compile.drain
}
