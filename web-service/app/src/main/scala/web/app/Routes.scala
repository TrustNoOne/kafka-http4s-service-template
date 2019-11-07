package web.app

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import web.service.GreetingsRepo
import JsonCodec._
import web.app.httpapi.HelloRequest

object httpapi {
  final case class HelloRequest(name: String)
}

object Routes {

  def helloWorldRoutes[F[_]: Sync](
      greetings: GreetingsRepo[F],
      helloRequester: HelloRequester[F]
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "recent-hellos" =>
        for {
          greetings <- greetings.recentGreetings
          resp <- Ok(greetings)
        } yield resp

      case req @ POST -> Root / "hello" =>
        for {
          HelloRequest(name) <- req.as[HelloRequest]
          _ <- helloRequester.requestHello(name)
          resp <- Ok()
        } yield resp

    }
  }
}
