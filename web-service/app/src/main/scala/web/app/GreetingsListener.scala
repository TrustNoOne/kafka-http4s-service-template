package web.app

import cats.effect.{ ConcurrentEffect, ContextShift, Timer }
import cats.implicits._
import fs2.kafka._
import io.circe.Decoder
import web.service.GreetingsRepo
import JsonCodec._
import fs2.Stream
import web.utils.Logging

import scala.util.control.NonFatal
import scala.concurrent.duration._

// TODO migrate to avro schemas
case class PersonGreeted(message: String)
object PersonGreeted {
  implicit val decoder: Decoder[PersonGreeted] = io.circe.derivation.deriveDecoder
}

trait GreetingsListener[F[_]] {
  def readGreetings(): F[Unit]
}

object GreetingsListener {
  def impl[F[_]: ConcurrentEffect: ContextShift: Timer](
      config: KafkaConfig,
      greetingsRepo: GreetingsRepo[F]
  ): GreetingsListener[F] =
    new GreetingsListenerImpl(config, greetingsRepo)
}

private class GreetingsListenerImpl[F[_]](
    config: KafkaConfig,
    greetingsRepo: GreetingsRepo[F]
)(
    implicit F: ConcurrentEffect[F],
    CS: ContextShift[F],
    T: Timer[F]
) extends GreetingsListener[F]
    with Logging {
  private val log = getLogger[F]

  private val consumerSettings =
    ConsumerSettings[F, Unit, PersonGreeted](
      keyDeserializer = Deserializer.unit[F],
      valueDeserializer = jsonDeserializer[F, PersonGreeted](log)
    ).withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(config.bootstrapServers)
      .withGroupId(config.groupId)

  private val stream: Stream[F, Unit] = consumerStream[F]
    .using(consumerSettings)
    .evalTap(_.subscribeTo(config.greetingsTopic))
    .evalTap(_ => log.info("Listening to greetings..."))
    .flatMap(_.parsedJsonStream)
    .mapAsync(25) { committable =>
      greetingsRepo
        .greetingReceived(committable.record.value.message)
        .as(committable.offset)
    }
    .through(commitBatchWithin(100, 5.seconds))
    .recoverWith {
      case NonFatal(e) =>
        // Restart the stream on failure. If hello world keeps failing, retry loops forever here
        Stream.eval(log.error(e.getMessage, e)) >>
          Stream.sleep(1.second) >>
          stream
    }

  def readGreetings: F[Unit] = stream.compile.drain
}
