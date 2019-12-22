package web

import java.time.OffsetDateTime

import org.http4s.{ Method, Request, Status }
import org.http4s.implicits._
import web.module.{ GreetingsRepo, HelloRequester, StoredGreeting }
import zio.test.mock.Expectation
import zio.test.mock.Expectation.{ unit, value }
import zio.interop.catz._
import zio.test._
import zio.test.Assertion._
import zio.{ test => _, _ }

object RecentHellosRouteTest
    extends DefaultRunnableSpec(
      suite("Recent Hellos Route")(
        testM("request hellos") {
          val postHello = Request[RIO[HelloRequester, *]](Method.POST, uri"/hello")
            .withEntity("""{"name":"yolo"}""")

          val mockedEnv: Expectation[HelloRequester, Nothing, Unit] =
            HelloRequester.requestHello(equalTo("yolo")) returns unit

          val result = Routes
            .helloRoute[HelloRequester]
            .orNotFound(postHello)
            .provideManaged(mockedEnv.managedEnv)

          assertM(result.map(_.status), equalTo(Status.Ok))
        },
        testM("returns recent greetings") {
          val testEnv: Expectation[GreetingsRepo, Nothing, Seq[StoredGreeting]] =
            GreetingsRepo.recentGreetings returns value(
              Seq(
                StoredGreeting("msg1", OffsetDateTime.parse("2007-12-03T10:15:30+01:00")),
                StoredGreeting("msg2", OffsetDateTime.parse("2007-12-04T10:15:30+01:00"))
              )
            )

          val request = Request[RIO[GreetingsRepo, *]](Method.GET, uri"/recent-hellos")

          val body = Routes
            .recentGreetingsRoute[GreetingsRepo]
            .orNotFound(request)
            .flatMap(_.as[String])
            .provideManaged(testEnv.managedEnv)

          val expected = """[{"message":"msg1","receivedAt":"2007-12-03T10:15:30+01:00"},""" +
            """{"message":"msg2","receivedAt":"2007-12-04T10:15:30+01:00"}]"""

          assertM(body, equalTo(expected))
        }
      )
    )
