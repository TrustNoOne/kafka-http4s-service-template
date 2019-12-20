package web.it

import com.dimafeng.testcontainers.Container
import zio._
import zio.duration._
import java.net.{ HttpURLConnection, URL }

import com.dimafeng.testcontainers.lifecycle.TestLifecycleAware
import org.testcontainers.lifecycle.TestDescription
import zio.clock.Clock
import zio.console.Console
import zio.test.{ Spec, TestAspect, TestFailure, TestSuccess, ZSpec }

object DockerAspect {

  private val numRetries = 30
  private def checkService(url: String): Task[Unit] =
    (for {
      _ <- console.putStrLn(s"Checking if $url is open...")
      conn <- ZIO(new URL(url).openConnection())
      _ <- ZIO(conn.connect())
      _ <- conn match {
            case c: HttpURLConnection =>
              ZIO(c.getResponseCode).flatMap {
                case x if x >= 200 && x < 400 => ZIO.unit
                case code                     => ZIO.fail(new IllegalStateException(s"Service check failed. Response code $code"))
              }

            case _ => ZIO.unit
          }

      _ <- ZIO(conn.getInputStream).bracket(
            release = is => ZIO(is.close()).orDie,
            use = is => ZIO(is.readAllBytes())
          )
    } yield ())
      .retry(Schedule.recurs(numRetries).addDelay(_ => 1.second))
      .provide(new Clock.Live with Console.Live)

  def dockerAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
      container: Container,
      serviceChecks: Seq[String] = Nil
  ): TestAspect[LowerR, UpperR, Any, Any, LowerS, UpperS] = {
    sys.addShutdownHook(try {
      container.stop() // failsafe -> shutdown on any kind of crash, ctrl-c etc
    } catch { case _: Throwable => })

    new TestAspect[LowerR, UpperR, Any, Any, LowerS, UpperS] {

      final def some[R >: LowerR <: UpperR, E >: Any <: Any, S >: LowerS <: UpperS, L](
          predicate: L => Boolean,
          spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] =
        spec
        // start and stop before/after suite
          .provideSomeManagedShared(
            ZManaged
              .makeEffect(container.start())(_ => container.stop())
              .tapM(_ => ZIO.traverse(serviceChecks)(checkService))
              .mapM(_ => ZIO.environment[R])
              .catchAllCause(c => ZManaged.fail(TestFailure.Runtime(c)))
          )
          //  per-test callbacks
          .transform[R, TestFailure[Any], L, TestSuccess[S]] {

            case Spec.TestCase(label, test) if predicate(label) =>
              val suiteDescription = new TestDescription {
                override def getTestId                 = label.toString
                override def getFilesystemFriendlyName = label.toString.replaceAll("[^a-zA-Z0-9_.-]", "_")
              }

              val perTest = ZManaged
                .makeEffect(
                  container match {
                    case container: TestLifecycleAware => container.beforeTest(suiteDescription)
                    case _                             => ()
                  }
                )(_ =>
                  container match {
                    case container: TestLifecycleAware => container.afterTest(suiteDescription, None)
                    case _                             => ()
                  }
                )
                .catchAllCause(c => ZManaged.fail(TestFailure.Runtime(c)))
                .use(_ => test)

              Spec.TestCase(label, perTest)

            case c => c
          }
    }
  }
}
