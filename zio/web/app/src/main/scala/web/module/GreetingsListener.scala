package web.module

import cats.syntax.applicativeError._
import fs2.kafka._
import fs2.kafka.vulcan.{ avroDeserializer, AvroSettings, SchemaRegistryClientSettings }
import fs2._
import web.{ AppConfig, HelloWorldConfig }
import _root_.vulcan.Codec
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.config.Config
import zio.logging.Logging

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait GreetingsListener {
  val greetingsListener: GreetingsListener.Service[Any]
}

object GreetingsListener {
  trait Service[-R] {
    // TODO error types?
    def readGreetings: ZIO[R, Throwable, Unit]
  }

  case class PersonGreeted(message: String)

  object PersonGreeted {
    implicit val codec: Codec[PersonGreeted] = Codec.record[PersonGreeted](
      name = "PersonGreeted",
      namespace = Some("backend.service")
    ) { field =>
      field("message", _.message)
        .map(PersonGreeted.apply)
    }
  }

  object > {
    def readGreetings: ZIO[GreetingsListener, Throwable, Unit] =
      ZIO.accessM(_.greetingsListener.readGreetings)
  }

  final case class Live private (
      greetingsRepo: GreetingsRepo.Service[Any],
      config: Config.Service[AppConfig],
      logger: Logging.Service[Any, String]
  ) extends GreetingsListener {

    override val greetingsListener: Service[Any] = new Service[Any] {

      private val consumerSettingsM = config.config.map(_.kafka).map { conf =>
        val avroSettings = AvroSettings(SchemaRegistryClientSettings[Task](conf.schemaRegistryBaseUrl))
        ConsumerSettings(
          keyDeserializer = Deserializer.unit[Task],
          valueDeserializer = avroDeserializer[PersonGreeted].using(avroSettings)
        ).withAutoOffsetReset(AutoOffsetReset.Latest)
          .withBootstrapServers(conf.bootstrapServers)
          .withGroupId(conf.groupId)
      }

      private def stream(
          conf: HelloWorldConfig,
          consumerSettings: ConsumerSettings[Task, Unit, PersonGreeted]
      )(implicit rt: Runtime[Any]): Stream[Task, Unit] =
        consumerStream[Task]
          .using(consumerSettings)
          .evalTap(_.subscribeTo(conf.greetingsTopic))
          .evalTap(_ => logger.info("Listening to greetings..."))
          .flatMap(_.stream)
          .mapAsync(25) { committable =>
            greetingsRepo
              .greetingReceived(committable.record.value.message)
              .as(committable.offset)
          }
          .through(commitBatchWithin(100, 5.seconds))
          .recoverWith {
            case NonFatal(e) =>
              // Restart the stream on failure. If hello world keeps failing, retry loops forever here
              Stream.eval(logger.error(e.getMessage, Cause.fail(e))) >>
                Stream.sleep[Task](1.second) >>
                stream(conf, consumerSettings)
          }

      final def readGreetings: ZIO[Any, Throwable, Unit] =
        for {
          rt <- ZIO.runtime[Any]
          conf <- config.config
          consumerSettings <- consumerSettingsM
          _ <- stream(conf.helloWorld, consumerSettings)(rt).compile.drain
        } yield ()
    }
  }

  object Live {
    def make: ZIO[GreetingsRepo with Config[AppConfig] with Logging[String], Nothing, GreetingsListener] =
      ZIO
        .environment[GreetingsRepo with Config[AppConfig] with Logging[String]]
        .map(e => new Live(e.greetingsRepo, e.config, e.logging))
  }

}
