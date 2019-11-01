package backend.helloworld

import cats.Applicative
import cats.implicits._

trait HelloWorld[F[_]] {
  def hello(n: HelloWorld.Name): F[HelloWorld.Greeting]
}

object HelloWorld {
  final case class Name(name: String) extends AnyVal

  final case class Greeting(greeting: String) extends AnyVal

  def impl[F[_]: Applicative]: HelloWorld[F] =
    (n: HelloWorld.Name) => Greeting("Hello, " + n.name).pure[F]
}
