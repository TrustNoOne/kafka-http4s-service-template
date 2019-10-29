package servicetmpl.app.web

import cats.effect.IO
import cats.implicits._
import minitest.SimpleTestSuite
import org.http4s.implicits._
import org.http4s.{ Method, Request, Response, Status }
import servicetmpl.{ HelloWorld, HelloWorldRepo }
import servicetmpl.HelloWorld.Greeting

object HelloWorldRouteSuite extends SimpleTestSuite {

  val helloWorld: HelloWorldRepo[IO] = new HelloWorldRepo[IO] {
    override def hello(n: HelloWorld.Name): IO[Greeting] = Greeting(s"${n.name} TEST").pure[IO]
    override def recentHellos: IO[List[Greeting]]        = IO.pure(List(Greeting("g1"), Greeting("g2")))
  }

  val retHelloWorld: Response[IO] = {
    val getHW = Request[IO](Method.GET, uri"/hello/world")
    Routes.helloWorldRoutes(helloWorld).orNotFound(getHW).unsafeRunSync()
  }

  val retRecentHellos: Response[IO] = {
    val getRecent = Request[IO](Method.GET, uri"/hellos")
    Routes.helloWorldRoutes(helloWorld).orNotFound(getRecent).unsafeRunSync()
  }

  test("return 200") {
    assertEquals(retHelloWorld.status, Status.Ok)
  }

  test("returns hello world") {
    assertEquals(
      received = retHelloWorld.as[String].unsafeRunSync(),
      expected = "{\"greeting\":\"world TEST\"}"
    )
  }

  test("return recent hellos") {
    assertEquals(
      received = retRecentHellos.as[String].unsafeRunSync(),
      expected = "[{\"greeting\":\"g1\"},{\"greeting\":\"g2\"}]"
    )
  }

}
