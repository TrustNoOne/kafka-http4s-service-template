package web.kafka

import fs2.kafka
import fs2.kafka.vulcan.{ avroSerializer, AvroSettings, SchemaRegistryClientSettings }
import fs2.kafka.{ producerResource, ProducerSettings }
import vulcan.Codec
import web.AppConfig
import zio.config.{ Config, config => getConfig }
import zio.interop.catz._
import zio.{ RIO, ZIO, ZManaged }

object KafkaProducer {

  def make[K: Codec, V: Codec]: ZManaged[Config[AppConfig], Throwable, kafka.KafkaProducer[RIO[Any, *], K, V]] =
    ZManaged.fromEffect(ZIO.runtime[Any]) flatMap { implicit rt =>
      ZManaged
        .fromEffect(getConfig[AppConfig])
        .map(_.kafka)
        .map { conf =>
          val avroSettings = AvroSettings(SchemaRegistryClientSettings[RIO[Any, *]](conf.schemaRegistryBaseUrl))
          ProducerSettings(
            keySerializer = avroSerializer[K].using(avroSettings),
            valueSerializer = avroSerializer[V].using(avroSettings)
          ).withBootstrapServers(conf.bootstrapServers)
        }
        .flatMap(producerSettings => producerResource[RIO[Any, *]].using(producerSettings).toManaged)
    }
}
