package web.app

import cats.Applicative
import io.circe.derivation._
import io.circe.{ Codec, Encoder }
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import web.service.StoredGreeting

object JsonCodec {
  implicit def circeEntityEncoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

  implicit val GreetingCodec: Codec[StoredGreeting] = deriveCodec
}
