package backend.service

import backend.{ AppConfig, HelloWorldConfig, KafkaConfig }
import backend.module.helloworld._
import backend.service.events.{ HelloRequested, PersonGreeted }
import cats.syntax.applicativeError._
import fs2.Stream
import fs2.kafka._
import fs2.kafka.vulcan._
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.config.Config
import zio.logging.Logging
import zio.logging.slf4j.logger

import scala.util.control.NonFatal
import scala.concurrent.duration._

trait KafkaGreeter {
  def kafkaGreeter: KafkaGreeter.Service[KafkaGreeter.Env]
}

object KafkaGreeter {
  type Env = Config[AppConfig] with Logging[String] with HelloWorld with Clock

  trait Service[R] {
    def start(): RIO[R, Unit]
  }

  trait Live extends KafkaGreeter {
    override def kafkaGreeter: Service[Env] =
      () =>
        for {
          conf <- config.config[AppConfig]
          rt <- ZIO.runtime[Env] // needed for interop

          avroSettings = makeAvroSettings(conf.kafka)
          _ <- stream(
                config = conf.helloWorld,
                consumerSettings = makeConsumerSettings(avroSettings, conf.kafka),
                producerSettings = makeProducerSettings(avroSettings, conf.kafka)
              )(rt).compile.drain

        } yield ()

    private def makeAvroSettings(conf: KafkaConfig): AvroSettings[RIO[Env, *]] =
      AvroSettings(SchemaRegistryClientSettings[RIO[Env, *]](conf.schemaRegistryBaseUrl))

    private def makeConsumerSettings(
        avroSettings: AvroSettings[RIO[Env, *]],
        conf: KafkaConfig
    ): ConsumerSettings[RIO[Env, *], Unit, HelloRequested] =
      ConsumerSettings(
        keyDeserializer = Deserializer.unit[RIO[Env, *]],
        valueDeserializer = avroDeserializer[HelloRequested].using(avroSettings)
      ).withAutoOffsetReset(AutoOffsetReset.Latest)
        .withBootstrapServers(conf.bootstrapServers)
        .withGroupId(conf.groupId)

    private def makeProducerSettings(
        avroSettings: AvroSettings[RIO[Env, *]],
        config: KafkaConfig
    ): ProducerSettings[RIO[Env, *], Unit, PersonGreeted] =
      ProducerSettings(
        keySerializer = Serializer.unit[RIO[Env, *]],
        valueSerializer = avroSerializer[PersonGreeted].using(avroSettings)
      ).withBootstrapServers(config.bootstrapServers)

    private def stream(
        config: HelloWorldConfig,
        consumerSettings: ConsumerSettings[RIO[Env, *], Unit, HelloRequested],
        producerSettings: ProducerSettings[RIO[Env, *], Unit, PersonGreeted]
    )(implicit rt: Runtime[Env]): Stream[RIO[Env, *], Unit] =
      consumerStream
        .using(consumerSettings)
        .evalTap(_.subscribeTo(config.requestsTopic))
        .evalTap(_ => logger.info("Greeter Started"))
        .flatMap(_.stream)
        .mapAsync(25) { committable =>
          processRequest(committable.record.value)
            .map { greetedEvent =>
              val record = ProducerRecord(config.greetingsTopic, (), greetedEvent)
              ProducerRecords.one(record, committable.offset)
            }
        }
        .through(produce(producerSettings))
        .map(_.passthrough)
        .through(commitBatchWithin(100, 5.seconds))
        .recoverWith {
          case NonFatal(e) =>
            // Restart the stream on failure. If hello world keeps failing, retry loops forever here
            Stream.eval(logger.error(e.getMessage, Cause.fail(e))) >>
              Stream.sleep[RIO[Env, *]](1.second) >>
              stream(config, consumerSettings, producerSettings)
        }

    private def processRequest(request: HelloRequested): RIO[Env, PersonGreeted] =
      hello(Name(request.name))
        .tap(greeting => logger.info(s"Sending greeting: ${greeting.greeting}"))
        .map(g => PersonGreeted(g.greeting))
  }

  object Live extends Live
}
