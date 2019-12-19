package backend.it

import java.io.File

import cats.instances.list._
import zio.{ test => _, _ }
import zio.test._
import zio.interop.catz._
import zio.interop.catz.implicits._
import Assertion._
import Fixture._
import backend.it.KafkaContainerAspect.{ BootstrapServers, SchemaRegistryUrl }
import backend.module.helloworld._
import backend.{ service, AppConfig, HelloWorldConfig, KafkaConfig }
import backend.service.{ KafkaGreeter, Schemas }
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic
import zio.clock.Clock
import zio.config.Config
import zio.logging.slf4j.Slf4jLogger

import scala.concurrent.duration._

private object KafkaContainerAspect {
  val BootstrapServers  = s"127.0.0.1:9092"
  val SchemaRegistryUrl = "http://127.0.0.1:8081"

  val aspect = DockerAspect.dockerAspect(
    container = DockerComposeContainer(new File("src/it/resources/docker-compose-kafka.yml")),
    serviceChecks = List(SchemaRegistryUrl)
  )
}

private object Fixture {

  val helloWorldConfig = HelloWorldConfig("hellos", "greets")
  val kafkaConfig      = KafkaConfig(BootstrapServers, "it-test-group", SchemaRegistryUrl)
  val appConfig        = AppConfig(kafkaConfig, helloWorldConfig)

  val consumerSettings =
    ConsumerSettings[Task, Unit, Array[Byte]](
      keyDeserializer = Deserializer.unit[Task],
      valueDeserializer = Deserializer.identity[Task]
    ).withBootstrapServers(BootstrapServers)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withGroupId("it-consumer-group")
      .withClientId("it-consumer")

  val producerSettings = ProducerSettings[Task, Unit, Array[Byte]](
    keySerializer = Serializer.unit[Task],
    valueSerializer = Serializer.identity[Task]
  ).withBootstrapServers(BootstrapServers)
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[Task]
    .withBootstrapServers(BootstrapServers)

  trait HelloWorldTest extends HelloWorld {
    override val helloWorld: HelloWorld.Service[Any] = (n: Name) =>
      ZIO.succeed(Greeting(s"test: ${n.name}"))
  }

  trait TestEnv
      extends Config[AppConfig]
      with Slf4jLogger.Live
      with Clock.Live
      with KafkaGreeter.Live
      with HelloWorldTest

  val env =
    Managed.succeed(new TestEnv {
      override def formatMessage(msg: String) = ZIO.succeed(msg)
      override def config: Config.Service[AppConfig] = new Config.Service[AppConfig] {
        def config = UIO.succeed(appConfig)
      }
    })

}

object KafkaGreeterServiceTest
    extends DefaultRunnableSpec(
      suite("Greeter Service")(
        testM("greets someone") {
          ZIO.runtime[TestEnv].flatMap {
            implicit rt =>
              import helloWorldConfig._
              val helloBytes = AvroTestUtil
                .serialize(SchemaRegistryUrl, requestsTopic, Schemas.HelloRequested, "name" -> "xyz")

              val enqueueRequestEvent = Stream
              // it could take a while for greeter to subscribe (latest), we retry a few times to be sure it's received
                .fixedDelay[Task](1.second)
                .take(5)
                .map(_ => ProducerRecords.one(ProducerRecord(requestsTopic, (), helloBytes)))
                .through(produce(producerSettings))
                .compile
                .drain

              val readGreetingEvent = consumerStream[Task]
                .using(consumerSettings)
                .evalTap(_.subscribeTo(greetingsTopic))
                .flatMap(_.stream)
                .evalMap(c => c.offset.commit.as(c.record.value))
                .head
                .compile
                .last

              val createTopics = adminClientResource(adminSettings).use(
                _.createTopics(
                  List(
                    new NewTopic(requestsTopic, 1, 1),
                    new NewTopic(greetingsTopic, 1, 1)
                  )
                )
              )

              val result = for {
                _ <- createTopics
                greeter <- service.start().fork
                requester <- enqueueRequestEvent.fork
                receivedEvent <- readGreetingEvent
                _ <- requester.interrupt
                _ <- greeter.interrupt
              } yield receivedEvent

              val expectedBytes = AvroTestUtil
                .serialize(SchemaRegistryUrl, greetingsTopic, Schemas.PersonGreeted, "message" -> "test: xyz")

              assertM(result, isSome(equalTo(expectedBytes)))
          }
        }
      ).provideManaged(Fixture.env) @@ KafkaContainerAspect.aspect
    )
