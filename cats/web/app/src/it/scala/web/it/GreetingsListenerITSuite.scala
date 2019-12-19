package web.it

import java.io.File

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.Stream
import fs2.kafka._
import fs2.kafka.vulcan.{AvroSettings, SchemaRegistryClientSettings, avroSerializer}
import org.apache.kafka.clients.admin.NewTopic
import web.app.GreetingsListener
import web.app.events.PersonGreeted
import web.service.GreetingsRepo

import scala.concurrent.duration._

object GreetingsListenerITSuite extends DockerTestSuite {
  val BootstrapServers = s"127.0.0.1:9092"
  val SchemaRegistryUrl = "http://127.0.0.1:8081"

  override val container = DockerComposeContainer(
    new File("src/it/resources/docker-compose-kafka.yml")
  )
  override val serviceChecks = List(SchemaRegistryUrl)

  val receivedGreetings = Ref[IO].of(Seq.empty[String]).unsafeRunSync()
  val greetingsRepo: GreetingsRepo[IO] = new GreetingsRepo[IO] {
    override def greetingReceived(message: String) = receivedGreetings.update(_ :+ message)
    override def recentGreetings = IO.raiseError(new Exception) // not used here
  }

  val greetingsTopic = config.helloWorld.greetingsTopic
  val greetingsListener = GreetingsListener.impl[IO](config.kafka, greetingsTopic, greetingsRepo)

  private val avroSettings =
    AvroSettings(SchemaRegistryClientSettings[IO](SchemaRegistryUrl))

  val producerSettings = ProducerSettings[IO, Unit, PersonGreeted](
      keySerializer = Serializer.unit[IO],
      valueSerializer = avroSerializer[PersonGreeted].using(avroSettings)
    )
    .withBootstrapServers(BootstrapServers)
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[IO]
    .withBootstrapServers(BootstrapServers)

  integrationTest("listener receives and processes greetings") {
    val enqueueGreeting = Stream
      // it could take a while for greeter to subscribe (latest), we retry a few times to be sure it's received
      .fixedDelay(1.second)
      .take(5)
      .map(_ => ProducerRecords(List(
        ProducerRecord(greetingsTopic, (), PersonGreeted("111")),
        ProducerRecord(greetingsTopic, (), PersonGreeted("222"))
      )))
      .covary[IO]
      .through(produce(producerSettings))
      .compile
      .drain

    val createTopics = adminClientResource(adminSettings)
      .use(_.createTopics(
        List(new NewTopic(greetingsTopic, 1, 1))
      ))

    val result = for {
      _ <- createTopics
      listener <- greetingsListener.readGreetings().start
      _ <- enqueueGreeting
      _ <- listener.cancel
      received <- receivedGreetings.get
    } yield received


    assertEquals(result.unsafeRunSync().take(2), List("111", "222"))
  }
}
