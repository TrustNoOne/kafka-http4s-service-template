package servicetmpl

import java.io.File

import cats.effect.IO
import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import fs2.Stream
import fs2.kafka._

object MySimpleITSuite extends DockerTestSuite {
  override val container = DockerComposeContainer(
    new File("src/it/resources/docker-compose-kafka.yml")
  )

  val KafkaPort = 9092

  val consumerSettings =
    ConsumerSettings[IO, String, String]
      .withBootstrapServers(s"127.0.0.1:$KafkaPort")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withGroupId("group")

  val producerSettings = ProducerSettings[IO, String, String]
    .withBootstrapServers(s"127.0.0.1:$KafkaPort")

  integrationTest("kafka works") {
    val producer = Stream
      .emit(ProducerRecords.one(ProducerRecord("topic", "key1", "value1")))
      .covary[IO]
      .through(produce(producerSettings))
      .compile
      .drain
      .start

    def processRecord(record: ConsumerRecord[String, String]) =
      IO.pure(record.key -> record.value)

    val consumer = consumerStream[IO]
      .using(consumerSettings)
      .evalTap(_.subscribeTo("topic"))
      .flatMap(_.stream)
      .evalMap { committable =>
        processRecord(committable.record)
          .flatTap(_ => committable.offset.commit)
      }
      .head
      .compile
      .last
      .start

    val receivedMsg = producer
      .flatMap(_.join)
      .flatMap(_ => consumer.flatMap(_.join))
      .unsafeRunSync()

    assertEquals(receivedMsg, Some("key1" -> "value1"))
  }
}
