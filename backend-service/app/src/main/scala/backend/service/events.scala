package backend.service

import io.circe.Codec
import io.circe.derivation._

object events {
  case class HelloRequested(name: String)
  object HelloRequested { implicit val codec: Codec[HelloRequested] = deriveCodec }

  case class PersonGreeted(message: String)
  object PersonGreeted { implicit val codec: Codec[PersonGreeted] = deriveCodec }
}
