package backend.helloworld

import cats.effect.IO
import minitest.SimpleTestSuite
import backend.helloworld.HelloWorld.{ Greeting, Name }

object HelloWorldTestSuite extends SimpleTestSuite {
  val helloWorld = HelloWorld.impl[IO]

  test("return hello xxx") {
    val result = helloWorld.hello(Name("xxx")).unsafeRunSync()
    assertEquals(result, Greeting("Hello, xxx"))
  }

}
