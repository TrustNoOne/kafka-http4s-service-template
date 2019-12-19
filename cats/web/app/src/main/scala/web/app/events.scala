package web.app

import vulcan.Codec

object events {
  final case class HelloRequested(name: String)
  object HelloRequested {
    implicit val codec: Codec[HelloRequested] = Codec.record[HelloRequested](
      name = "HelloRequested",
      namespace = Some("backend.service")
    ) { field =>
      field("name", _.name)
        .map(HelloRequested.apply)
    }
  }

  final case class PersonGreeted(message: String)
  object PersonGreeted {
    implicit val codec: Codec[PersonGreeted] = Codec.record[PersonGreeted](
      name = "PersonGreeted",
      namespace = Some("backend.service")
    ) { field =>
      field("message", _.message)
        .map(PersonGreeted.apply)
    }
  }
}
