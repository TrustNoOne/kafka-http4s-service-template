package web.app

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import web.service.GreetingsRepo
import JsonCodec._

object Routes {

  def helloWorldRoutes[F[_]: Sync](greetings: GreetingsRepo[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "recent-hellos" =>
        for {
          greetings <- greetings.recentGreetings
          resp <- Ok(greetings)
        } yield resp
    }
  }
}
