package web.module

import java.time.OffsetDateTime

import zio._
import zio.clock.Clock

trait GreetingsRepo {
  val greetingsRepo: GreetingsRepo.Service[Clock]
}

final case class StoredGreeting(message: String, receivedAt: OffsetDateTime)

object GreetingsRepo {

  trait Service[R] {
    def greetingReceived(message: String): ZIO[R, Nothing, Unit]
    def recentGreetings: ZIO[Any, Nothing, Seq[StoredGreeting]]
  }

  trait Live extends GreetingsRepo {
    // dependencies. Buffer is stateful, it must be created in an effect
    val liveGreetingsRepoBufferSize: Int
    val liveGreetingsRepoBufferRef: Ref[Seq[StoredGreeting]]

    override val greetingsRepo = new GreetingsRepo.Service[Clock] {
      override def greetingReceived(message: String): ZIO[Clock, Nothing, Unit] =
        clock.currentDateTime
          .map(StoredGreeting(message, _))
          .flatMap(greeting =>
            liveGreetingsRepoBufferRef.update(_.takeRight(liveGreetingsRepoBufferSize - 1) :+ greeting)
          )
          .unit

      override def recentGreetings: UIO[Seq[StoredGreeting]] = liveGreetingsRepoBufferRef.get
    }
  }

  object > {
    def greetingReceived(message: String): ZIO[GreetingsRepo with Clock, Nothing, Unit] =
      ZIO.accessM(_.greetingsRepo.greetingReceived(message))

    def recentGreetings: ZIO[GreetingsRepo, Nothing, Seq[StoredGreeting]] =
      ZIO.accessM(_.greetingsRepo.recentGreetings)
  }
}
