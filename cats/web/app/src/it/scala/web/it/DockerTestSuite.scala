package web.it

import java.net.{ HttpURLConnection, URL }

import cats.effect.{ ContextShift, IO, Timer }
import com.dimafeng.testcontainers.Container
import com.dimafeng.testcontainers.lifecycle.TestLifecycleAware
import minitest.TestSuite
import minitest.api.Void
import org.log4s
import org.testcontainers.lifecycle.TestDescription
import pureconfig.ConfigSource
import web.app.Config

import scala.concurrent.ExecutionContext

trait DockerTestSuite extends TestSuite[Unit] {
  private val log: log4s.Logger = log4s.getLogger(getClass)

  val config = ConfigSource.default.loadOrThrow[Config]

  @volatile private var stopped                   = false
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val contextSwitch: ContextShift[IO]    = IO.contextShift(executionContext)
  implicit val timer: Timer[IO]                   = IO.timer(executionContext)

  val container: Container
  val serviceChecks: Seq[String] = Nil

  private def checkService(url: String, retries: Int): Unit =
    try {
      log.info(s"Waiting until $url is open...")
      val conn = new URL(url).openConnection()
      conn.connect()
      conn match {
        case c: HttpURLConnection => assertEquals(c.getResponseCode, 200)
        case _                    =>
      }
      val is = conn.getInputStream
      is.readAllBytes()
      is.close()
    } catch {
      case _: Throwable if retries > 0 =>
        Thread.sleep(1000)
        checkService(url, retries - 1)
    }

  override def setupSuite(): Unit = {
    super.setupSuite()
    sys.addShutdownHook(if (!stopped) container.stop())
    container.start()
    serviceChecks foreach (checkService(_, retries = 30))
  }

  override def tearDownSuite(): Unit =
    try {
      super.tearDownSuite()
    } finally {
      container.stop()
      stopped = true
    }

  // do we need this?
  val suiteDescription = new TestDescription {
    override def getTestId                 = getClass.getSimpleName
    override def getFilesystemFriendlyName = getClass.getSimpleName
  }

  override def setup(): Unit =
    container match {
      case container: TestLifecycleAware => container.beforeTest(suiteDescription)
      case _                             => ()
    }

  override def tearDown(env: Unit): Unit =
    container match {
      case container: TestLifecycleAware => container.afterTest(suiteDescription, None)
      case _                             => ()
    }

  def integrationTest(name: String)(f: => Void): Unit = test(name)(_ => f)

}
