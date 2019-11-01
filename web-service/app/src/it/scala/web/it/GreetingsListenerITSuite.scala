package web.it

import java.io.File

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.clients.admin.NewTopic
import pureconfig.ConfigSource
import web.app.{Config, GreetingsListener}
import web.service.GreetingsRepo

import scala.concurrent.duration._

object GreetingsListenerITSuite extends DockerTestSuite {
  override val container = DockerComposeContainer(
    new File("src/it/resources/docker-compose-kafka.yml")
  )

  val KafkaPort = 9092

  val receivedGreetings = Ref[IO].of(Seq.empty[String]).unsafeRunSync()
  val greetingsRepo: GreetingsRepo[IO] = new GreetingsRepo[IO] {
    override def greetingReceived(message: String) = receivedGreetings.update(_ :+ message)
    override def recentGreetings = IO.raiseError(new Exception) // not used here
  }

  val config = ConfigSource.default.loadOrThrow[Config] // use reference conf in IT
  val greetingsTopic = config.kafka.greetingsTopic
  val greetingsListener = GreetingsListener.impl[IO](config.kafka, greetingsRepo)


  val producerSettings = ProducerSettings[IO, Unit, String]
    .withBootstrapServers(s"127.0.0.1:$KafkaPort")
    .withClientId("it-producer")

  val adminSettings = AdminClientSettings[IO]
    .withBootstrapServers(s"127.0.0.1:$KafkaPort")

  integrationTest("listener receives and processes greetings") {
    val enqueueGreeting = Stream
      // it could take a while for greeter to subscribe (latest), we retry a few times to be sure it's received
      .fixedDelay(1.second)
      .take(5)
      .map(_ => ProducerRecords(List(
        ProducerRecord(config.kafka.greetingsTopic, (), """{"message":"111"}"""),
        ProducerRecord(config.kafka.greetingsTopic, (), """{"message":"222"}""")
      )))
      .covary[IO]
      .through(produce(producerSettings))
      .compile
      .drain

    val createTopics = adminClientResource(adminSettings).use(_.createTopics(List(
        new NewTopic(greetingsTopic, 1, 1),
      )))

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
