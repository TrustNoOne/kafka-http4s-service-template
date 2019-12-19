package web.app

import cats.effect.{ ConcurrentEffect, ContextShift, Timer }
import cats.implicits._
import fs2.kafka._
import web.service.GreetingsRepo
import fs2.Stream
import fs2.kafka.vulcan.{ avroDeserializer, AvroSettings, SchemaRegistryClientSettings }
import web.utils.Logging
import web.app.events.PersonGreeted

import scala.util.control.NonFatal
import scala.concurrent.duration._

trait GreetingsListener[F[_]] {
  def readGreetings(): F[Unit]
}

object GreetingsListener {
  def impl[F[_]: ConcurrentEffect: ContextShift: Timer](
      config: KafkaConfig,
      greetingsTopic: String,
      greetingsRepo: GreetingsRepo[F]
  ): GreetingsListener[F] =
    new GreetingsListenerImpl(config, greetingsTopic, greetingsRepo)
}

private class GreetingsListenerImpl[F[_]](
    kafkaConfig: KafkaConfig,
    greetingsTopic: String,
    greetingsRepo: GreetingsRepo[F]
)(
    implicit F: ConcurrentEffect[F],
    CS: ContextShift[F],
    T: Timer[F]
) extends GreetingsListener[F]
    with Logging {
  private val log = getLogger[F]

  private val avroSettings =
    AvroSettings(SchemaRegistryClientSettings[F](kafkaConfig.schemaRegistry.baseUrl))

  private val consumerSettings =
    ConsumerSettings[F, Unit, PersonGreeted](
      keyDeserializer = Deserializer.unit[F],
      valueDeserializer = avroDeserializer[PersonGreeted].using(avroSettings)
    ).withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(kafkaConfig.bootstrapServers)
      .withGroupId(kafkaConfig.groupId)

  private val stream: Stream[F, Unit] = consumerStream[F]
    .using(consumerSettings)
    .evalTap(_.subscribeTo(greetingsTopic))
    .evalTap(_ => log.info("Listening to greetings..."))
    .flatMap(_.stream)
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

  def readGreetings(): F[Unit] = stream.compile.drain
}
