package servicetmpl.app.web

import cats.effect.{ ConcurrentEffect, Timer }
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import servicetmpl.HelloWorldRepo

object Server {

  def stream[F[_]: ConcurrentEffect: Timer](helloWorldRepo: HelloWorldRepo[F]): F[Unit] = {
    val httpApp = (
      Routes.helloWorldRoutes[F](helloWorldRepo)
    ).orNotFound

    val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

    BlazeServerBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(finalHttpApp)
      .serve

  }.compile.drain
}
