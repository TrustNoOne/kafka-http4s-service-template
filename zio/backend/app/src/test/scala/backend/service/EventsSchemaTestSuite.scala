package backend.service

import backend.service.events._
import org.apache.avro.Schema
import zio.test._
import zio.test.Assertion._

object Schemas {
  val HelloRequested =
    new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream("schemas/HelloRequested.asvc"))
  val PersonGreeted =
    new Schema.Parser().parse(getClass.getClassLoader.getResourceAsStream("schemas/PersonGreeted.asvc"))
}

object EventsSchemaTestSuite
    extends DefaultRunnableSpec(
      suite("Events Schemas")(
        test("HelloRequested schema") {
          assert(HelloRequested.codec.schema, isRight(equalTo(Schemas.HelloRequested)))
        },
        test("GreetingsReceived schema") {
          assert(PersonGreeted.codec.schema, isRight(equalTo(Schemas.PersonGreeted)))
        }
      )
    )
