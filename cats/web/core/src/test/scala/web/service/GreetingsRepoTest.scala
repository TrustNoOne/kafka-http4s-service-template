package web.service

import java.time.{ Instant, ZoneId }

import cats.Applicative
import cats.effect.{ Clock, IO }
import cats.implicits._
import minitest.SimpleTestSuite

import scala.concurrent.duration.TimeUnit

object GreetingsRepoTest extends SimpleTestSuite {
  def fixedClock[F[_]: Applicative](value: Long): Clock[F] = new Clock[F] {
    override def realTime(unit: TimeUnit)  = value.pure[F]
    override def monotonic(unit: TimeUnit) = value.pure[F]
  }

  val time      = Instant.parse("2007-12-03T10:15:30.00Z")
  val greetings = GreetingsRepo.impl[IO](size = 2, fixedClock(time.toEpochMilli))

  test("return the last n messages") {
    val result = for {
      r <- greetings
      _ <- r.greetingReceived("yolo1")
      _ <- r.greetingReceived("yolo2")
      _ <- r.greetingReceived("yolo3")
      result <- r.recentGreetings
    } yield result

    val zonedTime = time.atZone(ZoneId.systemDefault)
    assertEquals(result.unsafeRunSync(), Seq(StoredGreeting("yolo2", zonedTime), StoredGreeting("yolo3", zonedTime)))
  }

}
