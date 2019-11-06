package backend.service

import backend.service.events._
import minitest.SimpleTestSuite
import org.apache.avro.Schema

object Schemas {
  val HelloRequested =
    new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream("schemas/HelloRequested.asvc"))
  val PersonGreeted =
    new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream("schemas/PersonGreeted.asvc"))
}

object EventsSchemaTestSuite extends SimpleTestSuite {

  test("HelloRequested schema") {
    assertEquals(HelloRequested.codec.schema, Right(Schemas.HelloRequested))
  }

  test("GreetingsReceived schema") {
    assertEquals(PersonGreeted.codec.schema, Right(Schemas.PersonGreeted))
  }
}
