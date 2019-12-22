package backend.module.helloworld

import zio.ZIO

trait HelloWorld {
  val helloWorld: HelloWorld.Service[Any]
}

final case class Name(name: String) extends AnyVal
final case class Greeting(greeting: String) extends AnyVal

object HelloWorld {
  trait Service[R] {
    def hello(n: Name): ZIO[R, Nothing, Greeting]
  }

  trait Live extends HelloWorld {
    override val helloWorld: Service[Any] =
      (n: Name) => ZIO.succeed(Greeting("Hello, " + n.name))
  }

  object Live extends Live

  object > {
    def hello(name: Name): ZIO[HelloWorld, Nothing, Greeting] =
      ZIO.accessM(_.helloWorld.hello(name))
  }
}
