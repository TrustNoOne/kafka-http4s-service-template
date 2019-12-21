package web

import java.time.OffsetDateTime

import org.http4s.{ Method, Request, Status }
import org.http4s.implicits._
import web.module.{ GreetingsRepo, HelloRequester, StoredGreeting }
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

          for {
            received <- Ref.make("nothing received")
            testEnv = new HelloRequester {
              override val helloRequester = (name: String) => received.set(name)
            }

            resp <- Routes
                     .helloRoute(postHello)
                     .value
                     .provide(testEnv)

            statusIsOk = assert(resp.map(_.status), isSome(equalTo(Status.Ok)))
            yoloIsReceived <- assertM(received.get, equalTo("yolo"))

          } yield statusIsOk && yoloIsReceived
        },
        testM("returns recent greetings") {
          val testEnv = new GreetingsRepo {
            override val greetingsRepo = new GreetingsRepo.Service[Any] {
              override def greetingReceived(message: String) = ZIO.unit
              override def recentGreetings = ZIO.succeed(
                Seq(
                  StoredGreeting("msg1", OffsetDateTime.parse("2007-12-03T10:15:30+01:00")),
                  StoredGreeting("msg2", OffsetDateTime.parse("2007-12-04T10:15:30+01:00"))
                )
              )
            }
          }

          val request = Request[RIO[GreetingsRepo, *]](Method.GET, uri"/recent-hellos")

          val body = Routes
            .recentGreetingsRoute[GreetingsRepo]
            .orNotFound(request)
            .flatMap(_.as[String])
            .provide(testEnv)

          val expected = """[{"message":"msg1","receivedAt":"2007-12-03T10:15:30+01:00"},""" +
            """{"message":"msg2","receivedAt":"2007-12-04T10:15:30+01:00"}]"""

          assertM(body, equalTo(expected))
        }
      )
    )
