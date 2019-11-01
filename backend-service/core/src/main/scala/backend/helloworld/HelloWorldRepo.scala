package backend.helloworld

import cats.FlatMap
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._

trait HelloWorldRepo[F[_]] {
  def hello(name: HelloWorld.Name): F[HelloWorld.Greeting]

  def recentHellos: F[Seq[HelloWorld.Greeting]]
}

object HelloWorldRepo {
  implicit def HelloWorldRepo[F[_]](implicit ev: HelloWorldRepo[F]): HelloWorldRepo[F] = ev

  def impl[F[_]: Sync](helloWorld: HelloWorld[F], size: Int): F[HelloWorldRepo[F]] =
    Ref[F]
      .of(Vector.empty[HelloWorld.Greeting])
      .map(new HelloWorldRepoImpl(size, helloWorld, _))
}

private class HelloWorldRepoImpl[F[_]: FlatMap](
    size: Int,
    helloWorld: HelloWorld[F],
    buffer: Ref[F, Vector[HelloWorld.Greeting]]
) extends HelloWorldRepo[F] {

  override def hello(name: HelloWorld.Name): F[HelloWorld.Greeting] =
    helloWorld
      .hello(name)
      .flatMap { greeting =>
        buffer.modify(buf => (buf.takeRight(size - 1) :+ greeting, greeting))
      }

  override def recentHellos: F[Seq[HelloWorld.Greeting]] =
    buffer.get.widen[Seq[HelloWorld.Greeting]]
}
