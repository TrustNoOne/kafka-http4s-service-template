package backend.service

import cats.effect.{ ConcurrentEffect, ContextShift, Timer }
import cats.implicits._
import fs2._
import fs2.kafka._
import backend.helloworld.HelloWorld
import backend.service.events.{ HelloRequested, PersonGreeted }
import backend.utils.Logging
import backend.utils.KafkaJsonCodec._

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait KafkaGreeterService[F[_]] {
  def start(): F[Unit]
}

object KafkaGreeterService {
  def impl[F[_]: ConcurrentEffect: ContextShift: Timer](
      config: Config,
      helloWorld: HelloWorld[F]
  ): KafkaGreeterService[F] = new KafkaGreeterServiceImpl[F](config, helloWorld)
}

private class KafkaGreeterServiceImpl[F[_]: ContextShift: Timer](
    config: Config,
    helloWorld: HelloWorld[F]
)(implicit F: ConcurrentEffect[F])
    extends KafkaGreeterService[F]
    with Logging {
  private val log = getLogger[F]

  private val consumerSettings =
    ConsumerSettings[F, Unit, HelloRequested](
      keyDeserializer = Deserializer.unit[F],
      valueDeserializer = jsonDeserializer[F, HelloRequested](log)
    ).withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(config.kafka.bootstrapServers)
      .withGroupId(config.kafka.groupId)

  private val producerSettings = ProducerSettings[F, Unit, PersonGreeted](
    keySerializer = Serializer.unit[F],
    valueSerializer = jsonSerializer[F, PersonGreeted]
  ).withBootstrapServers(config.kafka.bootstrapServers)

  private val stream: Stream[F, Unit] = consumerStream[F]
    .using(consumerSettings)
    .evalTap(_.subscribeTo(config.helloWorld.requestsTopic))
    .evalTap(_ => log.info("Greeter Started"))
    .flatMap(_.parsedJsonStream)
    .mapAsync(25) { committable =>
      processRequest(committable.record.value).map { greetedEvent =>
        val record = ProducerRecord(config.helloWorld.greetingsTopic, (), greetedEvent)
        ProducerRecords.one(record, committable.offset)
      }
    }
    .through(produce(producerSettings))
    .map(_.passthrough)
    .through(commitBatchWithin(100, 5.seconds))
    .recoverWith {
      case NonFatal(e) =>
        // Restart the stream on failure. If hello world keeps failing, retry loops forever here
        Stream.eval(log.error(e.getMessage, e)) >>
          Stream.sleep(1.second) >>
          stream
    }

  private def processRequest(request: HelloRequested): F[PersonGreeted] =
    helloWorld
      .hello(HelloWorld.Name(request.name))
      .flatTap(greeting => log.info(s"Sending greeting: ${greeting.greeting}"))
      .map(g => PersonGreeted(g.greeting))

  def start(): F[Unit] = stream.compile.drain
}
