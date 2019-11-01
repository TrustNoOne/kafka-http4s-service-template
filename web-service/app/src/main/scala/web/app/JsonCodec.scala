package web.app

import java.nio.charset.StandardCharsets

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import fs2.kafka.{ CommittableConsumerRecord, Deserializer, KafkaConsumer }
import io.circe.derivation._
import io.circe.parser.decode
import io.circe.{ Codec, Decoder, Encoder }
import org.http4s.EntityEncoder
import org.http4s.circe._
import web.service.StoredGreeting
import web.utils.Logger

object JsonCodec {
  implicit def jsonEntityEncoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

  implicit val GreetingCodec: Codec[StoredGreeting] = deriveCodec

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

  implicit class KafkaJsonOps[F[_], K, V](val consumer: KafkaConsumer[F, K, V]) extends AnyVal {
    def parsedJsonStream: Stream[F, CommittableConsumerRecord[F, K, V]] =
      consumer.stream.filter(x => x.record.value != null)
  }
}
