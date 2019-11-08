package web.app

import cats.effect.{ ContextShift, Sync }
import cats.implicits._
import org.http4s.HttpRoutes
import tapir._
import tapir.json.circe._
import tapir.openapi.OpenAPI
import tapir.openapi.circe.yaml._
import tapir.docs.openapi._
import tapir.server.http4s._
import tapir.swagger.http4s.SwaggerHttp4s
import web.app.JsonCodec._
import web.app.httpapi.HelloRequest
import web.service.{ GreetingsRepo, StoredGreeting }

object httpapi {
  final case class HelloRequest(name: String)
}

object Routes {
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

  def openApiRoutes[F[_]: Sync: ContextShift]: HttpRoutes[F] = {
    val openApiDocs: OpenAPI = List(recentHellos, hello)
      .toOpenAPI("Hello World web app", "1.0.0")
    val openApiYml: String = openApiDocs.toYaml

    new SwaggerHttp4s(openApiYml).routes
  }

  def helloWorldRoutes[F[_]: Sync: ContextShift](
      greetings: GreetingsRepo[F],
      helloRequester: HelloRequester[F]
  ): HttpRoutes[F] = {
    val recentHelloRoute = recentHellos
      .toRoutes(_ => greetings.recentGreetings.map(_.asRight[Unit]))

    val helloRoute = hello
      .toRoutes(r => helloRequester.requestHello(r.name).map(_.asRight[Unit]))

    recentHelloRoute <+>
      helloRoute
  }
}
