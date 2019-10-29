package servicetmpl.helloworld

import cats.effect.IO
import cats.implicits._
import minitest.SimpleTestSuite
import servicetmpl.helloworld.HelloWorld.{ Greeting, Name }

object HelloWorldRepoTestSuite extends SimpleTestSuite {
  val helloWorld: HelloWorld[IO]         = (n: Name) => Greeting(n.name).pure[IO]
  val helloWorldRepo: HelloWorldRepo[IO] = HelloWorldRepo.impl[IO](helloWorld, size = 3).unsafeRunSync()

  test("return last 3 hello worlds") {
    val greetings = (1 to 4).toList
      .traverse(n => helloWorldRepo.hello(HelloWorld.Name(n.toString)))
      .unsafeRunSync()

    assertEquals(
      greetings,
      List(Greeting("1"), Greeting("2"), Greeting("3"), Greeting("4"))
    )
    assertEquals(
      helloWorldRepo.recentHellos.unsafeRunSync(),
      List(Greeting("2"), Greeting("3"), Greeting("4"))
    )
  }

}
