package web.it

import cats.instances.list._
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic
import web.AppConfig
import web.module.GreetingsListener.PersonGreeted
import web.module.{ GreetingsListener, GreetingsRepo, StoredGreeting }
import zio.clock.Clock
import zio.config.Config
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion._
import zio.test._
import zio._

import scala.concurrent.duration._

private object GreetingsListenerTestFixture extends ITConfig {
  trait TestEnv
      extends GreetingsListener.Live
      with GreetingsRepo
      with Config[AppConfig]
      with Slf4jLogger.Live
      with Clock.Live {
    def receivedGreetings: Ref[Seq[String]]
  }

  val testEnv = Managed.fromEffect(Ref.make(Seq.empty[String]).map { rcvGreets =>
    new TestEnv {
      override def receivedGreetings: Ref[Seq[String]]     = rcvGreets
      override def formatMessage(msg: String): UIO[String] = ZIO.succeed(msg)
      override def config: Config.Service[AppConfig]       = configService

      override val greetingsRepo: GreetingsRepo.Service[Clock] = new GreetingsRepo.Service[Clock] {
        override def greetingReceived(message: String): ZIO[Clock, Nothing, Unit] =
          rcvGreets.update(_ :+ message).unit
        override def recentGreetings: ZIO[Any, Nothing, Seq[StoredGreeting]] =
          ZIO.fail(new Exception("not used here")).orDie
      }
    }
  })
}

import web.it.GreetingsListenerTestFixture._

object GreetingsListenerTest
    extends DefaultRunnableSpec(
      suite("Greetings Listener")(
        testM("receives and processes greetings") {
          ZIO.runtime[TestEnv].flatMap {
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
                env <- ZIO.environment[TestEnv]
                _ <- createTopics
                listener <- GreetingsListener.>.readGreetings.fork
                _ <- enqueueGreeting
                _ <- listener.interrupt
                received <- env.receivedGreetings.get
              } yield received.take(2)

              assertM(result, equalTo(List("111", "222")))
          }
        }
      ).provideManaged(GreetingsListenerTestFixture.testEnv) @@ KafkaContainerAspect.aspect
    )
