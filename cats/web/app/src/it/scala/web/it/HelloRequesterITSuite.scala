package web.it

import java.io.File

import cats.effect.IO
import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.kafka._
import fs2.kafka.vulcan.{ avroDeserializer, avroSerializer, AvroSettings, SchemaRegistryClientSettings }
import org.apache.kafka.clients.admin.NewTopic
import web.app.HelloRequester
import web.app.events.{ HelloRequested, PersonGreeted }

object HelloRequesterITSuite extends DockerTestSuite {
  val BootstrapServers  = s"127.0.0.1:9092"
  val SchemaRegistryUrl = "http://127.0.0.1:8081"

  override val container = DockerComposeContainer(
    new File("src/it/resources/docker-compose-kafka.yml")
  )
  override val serviceChecks = List(SchemaRegistryUrl)

  val requestsTopic = config.helloWorld.requestsTopic

  private val avroSettings =
    AvroSettings(SchemaRegistryClientSettings[IO](SchemaRegistryUrl))

  val producerSettings = ProducerSettings[IO, Unit, PersonGreeted](
    keySerializer = Serializer.unit[IO],
    valueSerializer = avroSerializer[PersonGreeted].using(avroSettings)
  ).withBootstrapServers(BootstrapServers)
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[IO]
    .withBootstrapServers(BootstrapServers)

  val consumerSettings =
    ConsumerSettings[IO, Unit, HelloRequested](
      keyDeserializer = Deserializer.unit[IO],
      valueDeserializer = avroDeserializer[HelloRequested].using(avroSettings)
    ).withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(BootstrapServers)
      .withGroupId(getClass.getSimpleName)

  integrationTest("listener receives and processes greetings") {
    val requestHello = HelloRequester
      .impl[IO](config.kafka, config.helloWorld.requestsTopic)
      .use(_.requestHello("yolo"))

    val createTopics = adminClientResource(adminSettings)
      .use(_.createTopics(List(new NewTopic(requestsTopic, 1, 1))))

    val readHelloRequested = consumerStream[IO]
      .using(consumerSettings)
      .evalTap(_.subscribeTo(requestsTopic))
      .flatMap(_.stream)
      .evalMap(c => c.offset.commit.as(c.record.value))
      .head
      .compile
      .last

    val result = for {
      _ <- createTopics
      _ <- requestHello
      hr <- readHelloRequested
    } yield hr

    assertEquals(result.unsafeRunSync(), Some(HelloRequested("yolo")))
  }
}
