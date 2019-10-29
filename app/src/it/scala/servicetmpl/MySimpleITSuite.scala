package servicetmpl

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import fs2.kafka._

object MySimpleITSuite extends SimpleDockerTestSuite with DockerKafkaService {

  val consumerSettings =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"localhost:$KafkaAdvertisedPort")
      .withGroupId("group")

  val producerSettings =
    ProducerSettings[IO, String, String]
      .withBootstrapServers(s"localhost:$KafkaAdvertisedPort")

  test("kafka works") { _ =>
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
