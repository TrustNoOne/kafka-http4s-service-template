package web.app

import java.time.ZonedDateTime

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import minitest.SimpleTestSuite
import org.http4s.implicits._
import org.http4s.{ Method, Request, Response, Status }
import web.service.{ GreetingsRepo, StoredGreeting }

object RecentHellosRouteTest extends SimpleTestSuite {

  test("return recent hellos") {
    val helloWorld: GreetingsRepo[IO] = new GreetingsRepo[IO] {
      override def greetingReceived(message: String) = IO.unit
      override def recentGreetings =
        Seq(
          StoredGreeting("msg1", ZonedDateTime.parse("2007-12-03T10:15:30+01:00")),
          StoredGreeting("msg2", ZonedDateTime.parse("2007-12-04T10:15:30+01:00"))
        ).pure[IO]
    }

    val retRecentHellos: Response[IO] = {
      val getRecent = Request[IO](Method.GET, uri"/recent-hellos")
      Routes.helloWorldRoutes(helloWorld, null).orNotFound(getRecent).unsafeRunSync()
    }

    assertEquals(
      received = retRecentHellos.as[String].unsafeRunSync(),
      expected =
        """[{"message":"msg1","receivedAt":"2007-12-03T10:15:30+01:00"},""" +
          """{"message":"msg2","receivedAt":"2007-12-04T10:15:30+01:00"}]"""
    )
  }

  test("hello request") {
    val received                           = Ref[IO].of("nothing received").unsafeRunSync()
    val helloRequester: HelloRequester[IO] = received.set

    val requestHello: Response[IO] = {
      val postHello = Request[IO](Method.POST, uri"/hello")
        .withEntity("""{"name":"yolo"}""")

      Routes
        .helloWorldRoutes(null, helloRequester)
        .orNotFound(postHello)
        .unsafeRunSync()
    }

    assertEquals(requestHello.status, Status.Ok)
    assertEquals(received.get.unsafeRunSync(), "yolo")
  }

}
