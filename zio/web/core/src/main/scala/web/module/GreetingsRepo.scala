package web.module

import java.time.OffsetDateTime

import zio._
import zio.clock.Clock
import zio.macros.annotation.mockable

@mockable
trait GreetingsRepo {
  val greetingsRepo: GreetingsRepo.Service[Any]
}

final case class StoredGreeting(message: String, receivedAt: OffsetDateTime)

object GreetingsRepo {

  trait Service[R] {
    def greetingReceived(message: String): ZIO[R, Nothing, Unit]
    def recentGreetings: ZIO[R, Nothing, Seq[StoredGreeting]]
  }

  object > {
    def greetingReceived(message: String): ZIO[GreetingsRepo, Nothing, Unit] =
      ZIO.accessM(_.greetingsRepo.greetingReceived(message))

    def recentGreetings: ZIO[GreetingsRepo, Nothing, Seq[StoredGreeting]] =
      ZIO.accessM(_.greetingsRepo.recentGreetings)
  }

  final class Live private (
      size: Int,
      buffer: Ref[Seq[StoredGreeting]],
      clock: Clock.Service[Any]
  ) extends GreetingsRepo {

    override val greetingsRepo = new GreetingsRepo.Service[Any] {
      override def greetingReceived(message: String): ZIO[Any, Nothing, Unit] =
        clock.currentDateTime
          .map(time => StoredGreeting(message, time))
          .flatMap(greeting => buffer.update(_.takeRight(size - 1) :+ greeting).unit)

      override def recentGreetings: UIO[Seq[StoredGreeting]] = buffer.get
    }
  }

  object Live {
    def make(size: Int): ZIO[Clock, Nothing, GreetingsRepo] =
      for {
        buffer <- Ref.make(Seq.empty[StoredGreeting])
        clock <- ZIO.environment[Clock]
      } yield new Live(size, buffer, clock.clock)
  }

}
