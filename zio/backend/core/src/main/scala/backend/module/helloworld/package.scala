package backend.module

import zio._

package object helloworld {
  def hello(name: Name): URIO[HelloWorld, Greeting] =
    ZIO.accessM(_.helloWorld.hello(name))
}
