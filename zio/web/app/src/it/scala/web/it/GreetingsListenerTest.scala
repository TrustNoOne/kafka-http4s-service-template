package web.it

import cats.instances.list._
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic
import web.module.GreetingsListener.PersonGreeted
import web.module.{ GreetingsListener, GreetingsRepo, StoredGreeting }
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.macros.delegate.syntax._
import zio.macros.delegate._
import zio.test.Assertion._
import zio.test._
import zio._

import scala.concurrent.duration._

private object GreetingsListenerTestFixture extends ITConfig {
  trait GreetingsRepoMock extends GreetingsRepo {
    def receivedGreetings: Ref[Seq[String]]
  }

  val greetingsRepoMock: UIO[GreetingsRepoMock] =
    Ref.make(Seq.empty[String]).map { rcvGreets =>
      new GreetingsRepoMock {
        override val greetingsRepo: GreetingsRepo.Service[Any] = new GreetingsRepo.Service[Any] {
          override def greetingReceived(message: String): ZIO[Any, Nothing, Unit] =
            rcvGreets.update(_ :+ message).unit

          override def recentGreetings: ZIO[Any, Nothing, Seq[StoredGreeting]] =
            ZIO.fail(new Exception("not used here")).orDie
        }

        override def receivedGreetings: Ref[Seq[String]] = rcvGreets
      }
    }

  val testEnv =
    ZIO.succeed(itConfig) @@
      enrichWith(testLogging) @@
      enrichWithM(greetingsRepoMock) @@
      enrichWithM(GreetingsListener.Live.make)
}

import web.it.GreetingsListenerTestFixture._

object GreetingsListenerTest
    extends DefaultRunnableSpec(
      suite("Greetings Listener")(
        testM("receives and processes greetings") {
          ZIO.runtime[GreetingsRepoMock].flatMap {
            implicit rt =>
              val createTopics = adminClientResource(adminSettings)
                .use(_.createTopics(List(new NewTopic(helloWorldConfig.greetingsTopic, 1, 1))))

              val enqueueGreeting = fs2.Stream
              // it could take a while for greeter to subscribe (latest), we retry a few times to be sure it's received
                .fixedDelay[Task](1.second)
                .take(5)
                .map(_ =>
                  ProducerRecords(
                    List(
                      ProducerRecord(helloWorldConfig.greetingsTopic, (), PersonGreeted("111")),
                      ProducerRecord(helloWorldConfig.greetingsTopic, (), PersonGreeted("222"))
                    )
                  )
                )
                .through(produce(producerSettings))
                .compile
                .drain

              val result = for {
                env <- ZIO.environment[GreetingsRepoMock]
                _ <- createTopics
                listener <- GreetingsListener.>.readGreetings.fork
                _ <- enqueueGreeting
                _ <- listener.interrupt
                received <- env.receivedGreetings.get
              } yield received.take(2)

              assertM(result, equalTo(List("111", "222")))
          }
        }
      ).provideManaged(GreetingsListenerTestFixture.testEnv.toManaged_) @@ KafkaContainerAspect.aspect
    )
