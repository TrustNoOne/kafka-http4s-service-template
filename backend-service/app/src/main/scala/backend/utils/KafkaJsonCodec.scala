package backend.utils

import java.nio.charset.StandardCharsets

import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import fs2.kafka.{ CommittableConsumerRecord, Deserializer, KafkaConsumer, Serializer }
import io.circe.parser.decode
import io.circe.{ Decoder, Encoder }
import io.circe.syntax._

object KafkaJsonCodec {

  /**
    * Deserializes parse errors to nulls and logs them to avoid creating more objects.
    * Nulls will be skipped with the utility below
    */
  def jsonDeserializer[F[_], A: Decoder](logger: Logger[F])(implicit F: Sync[F]): Deserializer[F, A] =
    Deserializer.lift { bytes =>
      decode[A](new String(bytes, StandardCharsets.UTF_8)) match {
        case Left(error)    => logger.error("Invalid message", error).as(null.asInstanceOf[A])
        case Right(decoded) => F.pure(decoded)
      }
    }

  def jsonSerializer[F[_]: Sync, A: Encoder]: Serializer[F, A] =
    Serializer.string[F].contramap(_.asJson.noSpaces)

  implicit class KafkaJsonOps[F[_], K, V](val consumer: KafkaConsumer[F, K, V]) extends AnyVal {
    def parsedJsonStream: Stream[F, CommittableConsumerRecord[F, K, V]] =
      consumer.stream.filter(x => x.record.value != null)
  }

}
