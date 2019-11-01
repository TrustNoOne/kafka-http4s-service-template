package backend.it

import java.io.File

import cats.effect.IO
import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic
import pureconfig.ConfigSource
import backend.helloworld.HelloWorld
import backend.service.{Config, KafkaGreeterService}
import scala.concurrent.duration._

object KafkaGreeterServiceITSuite extends DockerTestSuite {
  override val container = DockerComposeContainer(
    new File("src/it/resources/docker-compose-kafka.yml")
  )

  val KafkaPort = 9092

  val helloWorld = new HelloWorld[IO] {
    override def hello(n: HelloWorld.Name): IO[HelloWorld.Greeting] =
      HelloWorld.Greeting(s"test: ${n.name}").pure[IO]
  }

  val config = ConfigSource.default.load[Config].toOption.get // use reference conf in IT
  val requestsTopic = config.helloWorld.requestsTopic
  val greetingsTopic = config.helloWorld.greetingsTopic
  val greeterService = KafkaGreeterService.impl[IO](config, helloWorld)

  val consumerSettings =
    ConsumerSettings[IO, Unit, String]
      .withBootstrapServers(s"127.0.0.1:$KafkaPort")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withGroupId("it-consumer-group")
      .withClientId("it-consumer")

  val producerSettings = ProducerSettings[IO, Unit, String]
    .withBootstrapServers(s"127.0.0.1:$KafkaPort")
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[IO]
    .withBootstrapServers(s"127.0.0.1:$KafkaPort")

  integrationTest("greeter emits greetings") {
    val enqueueRequestEvent = Stream
      // it could take a while for greeter to subscribe (latest), we retry a few times to be sure it's received
      .fixedDelay(1.second)
      .take(5)
      .map(_ => ProducerRecords.one(ProducerRecord(requestsTopic, (), """{"name":"xyz"}""")))
      .covary[IO]
      .through(produce(producerSettings))
      .compile
      .drain

    val readGreetingEvent = consumerStream[IO]
      .using(consumerSettings)
      .evalTap(_.subscribeTo(greetingsTopic))
      .flatMap(_.stream)
      .evalMap(c => c.offset.commit.as(c.record.value))
      .head
      .compile
      .last

    val createTopics = adminClientResource(adminSettings).use(_.createTopics(List(
        new NewTopic(requestsTopic, 1, 1),
        new NewTopic(greetingsTopic, 1, 1),
      )))

    val result = for {
      _ <- createTopics
      greeter <- greeterService.start().start
      requester <- enqueueRequestEvent.start
      receivedEvent <- readGreetingEvent
      _ <- requester.cancel
      _ <- greeter.cancel
    } yield receivedEvent

    assertEquals(result.unsafeRunSync(), Some("""{"message":"test: xyz"}"""))
  }
}
