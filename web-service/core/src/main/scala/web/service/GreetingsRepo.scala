package web.service

import java.time.{ Instant, ZoneId, ZonedDateTime }
import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import cats.effect.{ Clock, Sync }
import cats.implicits._

case class StoredGreeting(message: String, receivedAt: ZonedDateTime)

trait GreetingsRepo[F[_]] {
  def greetingReceived(message: String): F[Unit]

  def recentGreetings: F[Seq[StoredGreeting]]
}

object GreetingsRepo {

  def impl[F[_]: Sync](
      size: Int,
      clock: Clock[F]
  ): F[GreetingsRepo[F]] =
    Ref.of[F, Seq[StoredGreeting]](Vector.empty).map { buffer =>
      new GreetingsRepoImpl(size, clock, buffer)
    }
}

private class GreetingsRepoImpl[F[_]](
    size: Int,
    clock: Clock[F],
    buffer: Ref[F, Seq[StoredGreeting]]
)(implicit F: Sync[F])
    extends GreetingsRepo[F] {

  private val now = clock
    .realTime(TimeUnit.MILLISECONDS)
    .map(epoch => Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))

  def greetingReceived(message: String): F[Unit] =
    now
      .map(StoredGreeting(message, _))
      .flatMap(greeting => buffer.update(_.takeRight(size - 1) :+ greeting))

  def recentGreetings: F[Seq[StoredGreeting]] = buffer.get
}
