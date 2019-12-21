package web.it

import fs2.kafka.{ AdminClientSettings, AutoOffsetReset, ConsumerSettings, Deserializer, ProducerSettings, Serializer }
import fs2.kafka.vulcan.{ avroDeserializer, avroSerializer, AvroSettings, SchemaRegistryClientSettings }
import web.{ AppConfig, HelloWorldConfig, KafkaConfig, WebConfig }
import web.it.KafkaContainerAspect.{ BootstrapServers, SchemaRegistryUrl }
import web.module.GreetingsListener.PersonGreeted
import web.module.HelloRequester.HelloRequested
import zio.{ Task, UIO, ZIO }
import zio.config.Config
import zio.interop.catz._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

trait ITConfig {
  val helloWorldConfig = HelloWorldConfig("hellos", "greets")
  val kafkaConfig      = KafkaConfig(BootstrapServers, "it-test-group", SchemaRegistryUrl)
  val webConfig        = WebConfig("127.0.0.1", 11223)
  val appConfig        = AppConfig(webConfig, kafkaConfig, helloWorldConfig)

  val itConfig: Config[AppConfig] = new Config[AppConfig] {
    override def config: Config.Service[AppConfig] = new Config.Service[AppConfig] {
      override def config: UIO[AppConfig] = ZIO.succeed(appConfig)
    }
  }

  val testLogging: Logging[String] = new Slf4jLogger.Live {
    override def formatMessage(msg: String): UIO[String] = ZIO.succeed(msg)
  }

  val avroSettings =
    AvroSettings(SchemaRegistryClientSettings[Task](SchemaRegistryUrl))

  val producerSettings = ProducerSettings[Task, Unit, PersonGreeted](
    keySerializer = Serializer.unit[Task],
    valueSerializer = avroSerializer[PersonGreeted].using(avroSettings)
  ).withBootstrapServers(BootstrapServers)
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[Task]
    .withBootstrapServers(BootstrapServers)

  val consumerSettings =
    ConsumerSettings[Task, Unit, HelloRequested](
      keyDeserializer = Deserializer.unit[Task],
      valueDeserializer = avroDeserializer[HelloRequested].using(avroSettings)
    ).withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(BootstrapServers)
      .withGroupId(getClass.getSimpleName)
}
