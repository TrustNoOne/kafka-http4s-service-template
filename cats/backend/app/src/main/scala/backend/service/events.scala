package backend.service

import vulcan.Codec

// Important: Add tests to EventsSchemaTestSuite to make sure the right schema is generated
// Schemas are the way services interact with the outside world
// the .avsc files in test/resources/schemas are used as reference
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
