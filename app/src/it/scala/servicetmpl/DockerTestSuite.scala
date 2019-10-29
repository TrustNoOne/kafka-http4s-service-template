package servicetmpl

import cats.effect.{ContextShift, IO, Timer}
import com.whisk.docker.DockerKit
import minitest.TestSuite

import scala.concurrent.ExecutionContext

trait DockerTestSuite[Env] extends TestSuite[Env] with DockerKit with SpotifyDockerKit {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val contextSwitch: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO] = IO.timer(executionContext)

  override def setupSuite(): Unit = {
    super.setupSuite()
    startAllOrFail()
  }

  override def tearDownSuite(): Unit = {
    stopAllQuietly()
    super.tearDownSuite()
  }

}

trait SimpleDockerTestSuite extends DockerTestSuite[Unit] {
  override def setup(): Unit = ()
  override def tearDown(env: Unit): Unit = ()
}