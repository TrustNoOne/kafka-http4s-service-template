package servicetmpl

import cats.effect.{ContextShift, IO, Timer}
import com.dimafeng.testcontainers.Container
import com.dimafeng.testcontainers.lifecycle.TestLifecycleAware
import minitest.TestSuite
import minitest.api.Void
import org.testcontainers.lifecycle.TestDescription

import scala.concurrent.ExecutionContext


trait DockerTestSuite extends TestSuite[Unit] {
  @volatile private var stopped = false
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val contextSwitch: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO] = IO.timer(executionContext)

  val container: Container

  override def setupSuite(): Unit = {
    super.setupSuite()
    sys.addShutdownHook(if (!stopped) container.stop())
    container.start()
  }

  override def tearDownSuite(): Unit = {
    try {
      super.tearDownSuite()
    } finally {
      container.stop()
      stopped = true
    }
  }

  // do we need this?
  val suiteDescription = new TestDescription {
    override def getTestId = getClass.getSimpleName
    override def getFilesystemFriendlyName = getClass.getSimpleName
  }

  override def setup(): Unit = {
    container match {
      case container: TestLifecycleAware => container.beforeTest(suiteDescription)
      case _ => ()
    }
  }

  override def tearDown(env: Unit): Unit = {
    container match {
      case container: TestLifecycleAware => container.afterTest(suiteDescription, None)
      case _ => ()
    }
  }

  def integrationTest(name: String)(f: => Void): Unit = test(name)(_ => f)

}