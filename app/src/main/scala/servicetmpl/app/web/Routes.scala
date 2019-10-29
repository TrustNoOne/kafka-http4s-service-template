package servicetmpl.app.web

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import servicetmpl.app.JsonCodec.HelloWorldCodec._
import servicetmpl.app.JsonCodec._
import servicetmpl.helloworld.{ HelloWorld, HelloWorldRepo }

object Routes {

  def helloWorldRoutes[F[_]: Sync](H: HelloWorldRepo[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp

      case GET -> Root / "hellos" =>
        for {
          greetings <- H.recentHellos
          resp <- Ok(greetings)
        } yield resp
    }
  }
}
