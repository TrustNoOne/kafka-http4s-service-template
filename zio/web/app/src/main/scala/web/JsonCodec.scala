package web

import cats.Applicative
import cats.effect.Sync
import io.circe.derivation._
import io.circe.{ Codec, Decoder, Encoder }
import org.http4s.circe._
import org.http4s.{ EntityDecoder, EntityEncoder }
import web.httpapi.HelloRequest
import web.module.StoredGreeting

object JsonCodec {
  implicit def circeEntityEncoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]
  implicit def circeEntityDecoder[F[_]: Sync, A: Decoder]: EntityDecoder[F, A]        = jsonOf[F, A]

  implicit val GreetingCodec: Codec[StoredGreeting] = deriveCodec
  implicit val codec: Codec[HelloRequest]           = deriveCodec
}
