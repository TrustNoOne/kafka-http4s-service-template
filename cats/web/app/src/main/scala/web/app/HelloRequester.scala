package web.app

import cats.effect.{ ConcurrentEffect, ContextShift, Resource }
import cats.implicits._
import fs2.kafka._
import fs2.kafka.vulcan.{ avroSerializer, AvroSettings, SchemaRegistryClientSettings }
import web.app.events.HelloRequested

trait HelloRequester[F[_]] {
  def requestHello(name: String): F[Unit]
}

object HelloRequester {
  def impl[F[_]: ConcurrentEffect: ContextShift](
      kafkaConfig: KafkaConfig,
      requestsTopic: String
  ): Resource[F, HelloRequester[F]] = {
    val avroSettings =
      AvroSettings(SchemaRegistryClientSettings[F](kafkaConfig.schemaRegistry.baseUrl))

    val producerSettings =
      ProducerSettings[F, Unit, HelloRequested](
        keySerializer = Serializer.unit[F],
        valueSerializer = avroSerializer[HelloRequested].using(avroSettings)
      ).withBootstrapServers(kafkaConfig.bootstrapServers)

    producerResource[F]
      .using(producerSettings)
      .map(producer => new HelloRequesterImpl[F](producer, requestsTopic))
  }
}

private class HelloRequesterImpl[F[_]: ConcurrentEffect: ContextShift](
    producer: KafkaProducer[F, Unit, HelloRequested],
    requestsTopic: String
) extends HelloRequester[F] {

  override def requestHello(name: String): F[Unit] = {
    val record = ProducerRecords.one(ProducerRecord(requestsTopic, (), HelloRequested(name)))
    producer.produce(record).flatten.map(_.passthrough)
  }
}
