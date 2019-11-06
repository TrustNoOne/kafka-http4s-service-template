package backend.it

import java.io.File

import backend.helloworld.HelloWorld
import backend.service.{KafkaGreeterService, Schemas}
import cats.effect.IO
import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic

import scala.concurrent.duration._

object KafkaGreeterServiceITSuite extends DockerTestSuite {
  val BootstrapServers = s"127.0.0.1:9092"
  val SchemaRegistryUrl = "http://127.0.0.1:8081"

  override val container = DockerComposeContainer(
    new File("src/it/resources/docker-compose-kafka.yml")
  )
  override val serviceChecks = List(SchemaRegistryUrl)

  val helloWorld = new HelloWorld[IO] {
    override def hello(n: HelloWorld.Name): IO[HelloWorld.Greeting] =
      HelloWorld.Greeting(s"test: ${n.name}").pure[IO]
  }

  val requestsTopic = config.helloWorld.requestsTopic
  val greetingsTopic = config.helloWorld.greetingsTopic
  val greeterService = KafkaGreeterService.impl[IO](config, helloWorld)

  val consumerSettings =
    ConsumerSettings[IO, Unit, Array[Byte]]
      .withBootstrapServers(BootstrapServers)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withGroupId("it-consumer-group")
      .withClientId("it-consumer")

  val producerSettings = ProducerSettings[IO, Unit, Array[Byte]]
    .withBootstrapServers(BootstrapServers)
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[IO]
    .withBootstrapServers(BootstrapServers)

  integrationTest("greeter emits greetings") {
    val helloBytes = AvroTestUtil.serialize(SchemaRegistryUrl, requestsTopic, Schemas.HelloRequested,"name" -> "xyz")

    val enqueueRequestEvent = Stream
      // it could take a while for greeter to subscribe (latest), we retry a few times to be sure it's received
      .fixedDelay(1.second)
      .take(5)
      .map(_ => ProducerRecords.one(ProducerRecord(requestsTopic, (), helloBytes)))
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

    val expectedBytes = AvroTestUtil.serialize(SchemaRegistryUrl, greetingsTopic, Schemas.PersonGreeted,"message" -> "test: xyz")
    assertEquals(result.unsafeRunSync().get.toSeq, expectedBytes.toSeq)
  }
}
