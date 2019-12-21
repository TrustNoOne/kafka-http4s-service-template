package web

import cats.implicits._
import web.module.{ GreetingsRepo, HelloRequester, StoredGreeting }
import zio.RIO
import zio.interop.catz._
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

object httpapi {
  final case class HelloRequest(name: String)
}

object Routes {
  type RoutesEnv = GreetingsRepo with HelloRequester

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

  def openApiRoutes[R <: RoutesEnv] = {
    val openApiDocs: OpenAPI = List(recentHellos, hello)
      .toOpenAPI("Hello World web app", "1.0.0")
    val openApiYml: String = openApiDocs.toYaml

    new SwaggerHttp4s(openApiYml).routes[RIO[R, *]]
  }

  def recentGreetingsRoute[R <: GreetingsRepo]: HttpRoutes[RIO[R, *]] =
    recentHellos
      .toRoutes(_ => GreetingsRepo.>.recentGreetings.map(_.asRight[Unit]))

  def helloRoute[R <: HelloRequester]: HttpRoutes[RIO[R, *]] =
    hello
      .toRoutes(r => HelloRequester.>.requestHello(r.name).map(_.asRight[Unit]))

  def appRoutes[R <: RoutesEnv]: HttpRoutes[RIO[R, *]] =
    recentGreetingsRoute[R] <+>
      helloRoute[R]

}
