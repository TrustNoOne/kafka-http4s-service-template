package web.it

import cats.instances.list._
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic
import web.AppConfig
import web.module.HelloRequester.HelloRequested
import web.module.{ HelloRequester, KafkaProducerBuilder }
import zio.config.Config
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.test.Assertion._
import zio.test._
import zio.{ Task, ZIO }

private object HelloRequesterTestFixture extends ITConfig {

  trait TestEnv extends HelloRequester.Live with Config[AppConfig]

  val testEnv = KafkaProducerBuilder.Live.kafkaProducerBuilder
    .getKafkaProducer[Unit, HelloRequested]
    .map(prod =>
      new TestEnv {
        override val kafkaHelloRequestedProducer = prod
        override def config                      = configService
      }
    )
    .provide(new Config[AppConfig] {
      override def config: Config.Service[AppConfig] = configService
    })
    .orDie
}

import web.it.HelloRequesterTestFixture._

object HelloRequesterTest
    extends DefaultRunnableSpec(
      suite("Hello Requester")(
        testM("requests hellos") {
          ZIO.runtime[TestEnv].flatMap {
            implicit rt =>
              val createTopics = adminClientResource(adminSettings)
                .use(_.createTopics(List(new NewTopic(helloWorldConfig.requestsTopic, 1, 1))))

              val readHelloRequested = consumerStream[Task]
                .using(consumerSettings)
                .evalTap(_.subscribeTo(helloWorldConfig.requestsTopic))
                .flatMap(_.stream)
                .evalMap(c => c.offset.commit.as(c.record.value))
                .head
                .compile
                .last

              val result = for {
                _ <- createTopics
                _ <- HelloRequester.>.requestHello("yolo")
                hr <- readHelloRequested
              } yield hr

              assertM(result, isSome(equalTo(HelloRequested("yolo"))))
          }
        }
      ).provideManaged(HelloRequesterTestFixture.testEnv) @@ KafkaContainerAspect.aspect
    )
