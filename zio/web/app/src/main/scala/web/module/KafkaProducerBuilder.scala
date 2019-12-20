package web.module

import fs2.kafka
import fs2.kafka.{ producerResource, ProducerSettings }
import fs2.kafka.vulcan.{ avroSerializer, AvroSettings, SchemaRegistryClientSettings }
import vulcan.Codec
import web.AppConfig
import zio.{ RIO, ZIO, ZManaged }
import zio.interop.catz._
import zio.config.{ Config, config => getConfig }

trait KafkaProducerBuilder {
  val kafkaProducerBuilder: KafkaProducerBuilder.Service[KafkaProducerBuilder.Env]
}

object KafkaProducerBuilder {
  type Env = Config[AppConfig]

  trait Service[R] {
    def getKafkaProducer[K: Codec, V: Codec]: ZManaged[R, Throwable, kafka.KafkaProducer[RIO[R, *], K, V]]
  }

  trait Live extends KafkaProducerBuilder {

    override val kafkaProducerBuilder: Service[Env] = new Service[Env] {
      private def producerSettingsM[K: Codec, V: Codec] =
        ZManaged
          .fromEffect(getConfig[AppConfig])
          .map(_.kafka)
          .map { conf =>
            val avroSettings = AvroSettings(SchemaRegistryClientSettings[RIO[Env, *]](conf.schemaRegistryBaseUrl))
            ProducerSettings(
              keySerializer = avroSerializer[K].using(avroSettings),
              valueSerializer = avroSerializer[V].using(avroSettings)
            ).withBootstrapServers(conf.bootstrapServers)
          }

      override def getKafkaProducer[K: Codec, V: Codec]
          : ZManaged[Env, Throwable, kafka.KafkaProducer[RIO[Env, *], K, V]] =
        ZManaged.fromEffect(ZIO.runtime[Env]) flatMap { implicit rt =>
          producerSettingsM[K, V].flatMap(producerSettings =>
            producerResource[RIO[Env, *]].using(producerSettings).toManaged
          )
        }
    }
  }

  object Live extends Live
}
