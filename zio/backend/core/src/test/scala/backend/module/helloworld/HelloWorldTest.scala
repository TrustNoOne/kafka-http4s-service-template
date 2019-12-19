package backend.module
package helloworld

import zio.test._
import Assertion._

object HelloWorldTest
    extends DefaultRunnableSpec(
      suite("HelloWorld")(
        testM("return hello xxxx") {
          checkM(Gen.anyString) { name =>
            val result         = helloworld.hello(Name(name))
            val expectedResult = Greeting(s"Hello, $name")
            assertM(result, equalTo(expectedResult))
              .provide(HelloWorld.Live)
          }
        }
      )
    )
