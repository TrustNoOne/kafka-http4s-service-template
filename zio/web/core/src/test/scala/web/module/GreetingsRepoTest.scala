package web.module

import java.time.{ Instant, ZoneOffset }

import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

private object Fixture {
  val repoM = GreetingsRepo.Live.make(size = 2)

  val time1 = Instant.parse("2007-12-03T10:15:30.00Z").atOffset(ZoneOffset.UTC)
  val time2 = Instant.parse("2007-12-13T20:00:11.00Z").atOffset(ZoneOffset.UTC)
}

import web.module.Fixture._

object GreetingsRepoTest
    extends DefaultRunnableSpec(
      suite("GreetingsRepo")(
        testM("should return last N greetings") {
          for {
            r <- repoM
            _ <- TestClock.setDateTime(time1)
            _ <- r.greetingsRepo.greetingReceived("yolo1")
            _ <- r.greetingsRepo.greetingReceived("yolo2")
            _ <- TestClock.setDateTime(time2)
            _ <- r.greetingsRepo.greetingReceived("yolo3")
            result <- r.greetingsRepo.recentGreetings
          } yield {
            val expected = Seq(StoredGreeting("yolo2", time1), StoredGreeting("yolo3", time2))
            assert(result, equalTo(expected))
          }
        }
      )
    )
