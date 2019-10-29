package servicetmpl.app.kafka

import cats.effect.{ ConcurrentEffect, ContextShift, Timer }
import cats.implicits._
import fs2._
import fs2.kafka._
import org.apache.logging.log4j.LogManager
import servicetmpl.app.JsonCodec.HelloWorldCodec._
import servicetmpl.app.JsonCodec._
import servicetmpl.{ HelloWorld, HelloWorldRepo }

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait KafkaHelloWorld[F[_]] {
  def start(): F[Unit]
}

object KafkaHelloWorld {
  def impl[F[_]: ConcurrentEffect: ContextShift: Timer](
      config: KafkaConfig,
      helloWorld: HelloWorldRepo[F]
  ): KafkaHelloWorld[F] = new KafkaHelloWorldImpl[F](config, helloWorld)
}

private class KafkaHelloWorldImpl[F[_]: ContextShift: Timer](
    config: KafkaConfig,
    helloWorld: HelloWorldRepo[F]
)(implicit F: ConcurrentEffect[F])
    extends KafkaHelloWorld[F] {

  private val logger = LogManager.getLogger(getClass)

  private val consumerSettings =
    ConsumerSettings[F, Unit, HelloWorld.Name](
      keyDeserializer = Deserializer.unit[F],
      valueDeserializer = jsonDeserializer[F, HelloWorld.Name](logger)
    ).withAutoOffsetReset(AutoOffsetReset.Latest)
      .withBootstrapServers(config.bootstrapServers)
      .withGroupId(config.groupId)

  private val stream: Stream[F, Unit] = consumerStream[F]
    .using(consumerSettings)
    .evalTap(_.subscribeTo(config.helloWorldRequestTopic))
    .flatMap(_.parsedJsonStream)
    .mapAsync(25) { committable =>
      helloWorld
        .hello(committable.record.value)
        .flatMap(greeting => F.delay(logger.info(s"Greeting: ${greeting.greeting}")))
        .as(committable.offset)
    }
    .through(commitBatchWithin(100, 5.seconds))
    .recoverWith {
      case NonFatal(e) =>
        Stream.eval(F.delay(logger.error(e.getMessage, e))) >>
          Stream.sleep(1.second) >>
          stream
    }

  def start(): F[Unit] = stream.compile.drain
}
