package backend.service

import backend.{ AppConfig, HelloWorldConfig, KafkaConfig }
import backend.module.helloworld._
import backend.service.events.{ HelloRequested, PersonGreeted }
import cats.syntax.applicativeError._
import fs2.Stream
import fs2.kafka._
import fs2.kafka.vulcan._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.config.Config
import zio.logging.Logging

import scala.util.control.NonFatal
import scala.concurrent.duration._

trait KafkaGreeter {
  def kafkaGreeter: KafkaGreeter.Service[Any]
}

object KafkaGreeter {
  trait Service[R] {
    def start(): RIO[R, Unit]
  }

  object > {
    def start(): RIO[KafkaGreeter, Unit] =
      ZIO.accessM(_.kafkaGreeter.start())
  }

  class Live private (
      helloWorld: HelloWorld.Service[Any],
      config: Config.Service[AppConfig],
      logger: Logging.Service[Any, String]
  ) extends KafkaGreeter {

    override def kafkaGreeter: Service[Any] =
      () =>
        for {
          conf <- config.config
          rt <- ZIO.runtime[Any] // needed for interop

          avroSettings = makeAvroSettings(conf.kafka)
          _ <- stream(
                config = conf.helloWorld,
                consumerSettings = makeConsumerSettings(avroSettings, conf.kafka),
                producerSettings = makeProducerSettings(avroSettings, conf.kafka)
              )(rt).compile.drain

        } yield ()

    private def makeAvroSettings(conf: KafkaConfig): AvroSettings[Task] =
      AvroSettings(SchemaRegistryClientSettings[Task](conf.schemaRegistryBaseUrl))

    private def makeConsumerSettings(
        avroSettings: AvroSettings[Task],
        conf: KafkaConfig
    ): ConsumerSettings[Task, Unit, HelloRequested] =
      ConsumerSettings(
        keyDeserializer = Deserializer.unit[Task],
        valueDeserializer = avroDeserializer[HelloRequested].using(avroSettings)
      ).withAutoOffsetReset(AutoOffsetReset.Latest)
        .withBootstrapServers(conf.bootstrapServers)
        .withGroupId(conf.groupId)

    private def makeProducerSettings(
        avroSettings: AvroSettings[Task],
        config: KafkaConfig
    ): ProducerSettings[Task, Unit, PersonGreeted] =
      ProducerSettings(
        keySerializer = Serializer.unit[Task],
        valueSerializer = avroSerializer[PersonGreeted].using(avroSettings)
      ).withBootstrapServers(config.bootstrapServers)

    private def stream(
        config: HelloWorldConfig,
        consumerSettings: ConsumerSettings[Task, Unit, HelloRequested],
        producerSettings: ProducerSettings[Task, Unit, PersonGreeted]
    )(implicit rt: Runtime[Any]): Stream[Task, Unit] =
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
              Stream.sleep[Task](1.second) >>
              stream(config, consumerSettings, producerSettings)
        }

    private def processRequest(request: HelloRequested): Task[PersonGreeted] =
      helloWorld
        .hello(Name(request.name))
        .tap(greeting => logger.info(s"Sending greeting: ${greeting.greeting}"))
        .map(g => PersonGreeted(g.greeting))
  }

  object Live {
    def make: ZIO[Config[AppConfig] with Logging[String] with HelloWorld, Nothing, KafkaGreeter] =
      ZIO
        .environment[Config[AppConfig] with Logging[String] with HelloWorld]
        .map(e => new Live(e.helloWorld, e.config, e.logging))
  }
}
