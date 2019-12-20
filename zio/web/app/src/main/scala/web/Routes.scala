package web

import cats.implicits._
import cats.arrow.FunctionK
import web.module.{ GreetingsRepo, HelloRequester, StoredGreeting }
import zio.RIO
import zio.clock.Clock
import zio.logging.Logging
import zio.interop.catz._
import cats.implicits._
import org.http4s.HttpRoutes
import tapir._
import tapir.json.circe._
import tapir.openapi.OpenAPI
import tapir.openapi.circe.yaml._
import tapir.docs.openapi._
import tapir.server.http4s._
import tapir.swagger.http4s.SwaggerHttp4s
import web.JsonCodec._
import web.httpapi.HelloRequest
import zio.config.Config

object httpapi {
  final case class HelloRequest(name: String)
}

object Routes {
  type Env = Config[AppConfig] with Logging[String] with Clock with GreetingsRepo with HelloRequester

  import endpoints._

  object endpoints {
    val recentHellos: Endpoint[Unit, Unit, Seq[StoredGreeting], Nothing] = endpoint.get
      .in("recent-hellos")
      .out(jsonBody[Seq[StoredGreeting]])

    val hello: Endpoint[HelloRequest, Unit, Unit, Nothing] = endpoint.post
      .in("hello")
      .in(
        jsonBody[HelloRequest]
          .description("The hello request")
          .example(HelloRequest(name = "Giovanni"))
      )
  }

  val openApiRoutes = {
    val openApiDocs: OpenAPI = List(recentHellos, hello)
      .toOpenAPI("Hello World web app", "1.0.0")
    val openApiYml: String = openApiDocs.toYaml

    new SwaggerHttp4s(openApiYml).routes[RIO[Env, *]]
  }

  val recentGreetingsRoute: HttpRoutes[RIO[GreetingsRepo, *]] = recentHellos
    .toRoutes(_ => GreetingsRepo.>.recentGreetings.map(_.asRight[Unit]))

  val helloRoute: HttpRoutes[RIO[HelloRequester with HelloRequester.Env, *]] = hello
    .toRoutes(r => HelloRequester.>.requestHello(r.name).map(_.asRight[Unit]))

  val appRoutes = {
    // T >: Env and -R is contravariant in RIO, couldn't find a way without the cast
    type InnerRoute[R, _] = HttpRoutes[RIO[R, *]]
    def lift[R >: Env]: FunctionK[InnerRoute[R, *], InnerRoute[Env, *]] =
      new FunctionK[InnerRoute[R, *], InnerRoute[Env, *]] {
        override def apply[A](fa: InnerRoute[R, A]): InnerRoute[Env, A] =
          fa.asInstanceOf[InnerRoute[Env, A]]
      }

    lift(recentGreetingsRoute) <+>
      lift(helloRoute)
  }

}
