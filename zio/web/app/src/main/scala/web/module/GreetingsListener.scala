package web.module

import cats.syntax.applicativeError._
import fs2.kafka._
import fs2.kafka.vulcan.{ avroDeserializer, AvroSettings, SchemaRegistryClientSettings }
import fs2._
import web.{ AppConfig, HelloWorldConfig }
import _root_.vulcan.Codec
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.config.{ Config, config => getConfig }
import zio.logging.Logging
import zio.logging.slf4j.logger

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait GreetingsListener {
  val greetingsListener: GreetingsListener.Service[GreetingsListener.Env]
}

object GreetingsListener {
  type Env = GreetingsRepo with Config[AppConfig] with Logging[String] with Clock

  trait Service[R] {
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
    def readGreetings: ZIO[GreetingsListener with Env, Throwable, Unit] =
      ZIO.accessM(_.greetingsListener.readGreetings)
  }

  trait Live extends GreetingsListener {
    override val greetingsListener: Service[Env] = new Service[Env] {

      private val consumerSettingsM = getConfig[AppConfig].map(_.kafka).map { conf =>
        val avroSettings = AvroSettings(SchemaRegistryClientSettings[RIO[Env, *]](conf.schemaRegistryBaseUrl))
        ConsumerSettings(
          keyDeserializer = Deserializer.unit[RIO[Env, *]],
          valueDeserializer = avroDeserializer[PersonGreeted].using(avroSettings)
        ).withAutoOffsetReset(AutoOffsetReset.Latest)
          .withBootstrapServers(conf.bootstrapServers)
          .withGroupId(conf.groupId)
      }

      private def stream(
          conf: HelloWorldConfig,
          consumerSettings: ConsumerSettings[RIO[Env, *], Unit, PersonGreeted]
      )(implicit rt: Runtime[Env]): Stream[RIO[Env, *], Unit] =
        consumerStream[RIO[Env, *]]
          .using(consumerSettings)
          .evalTap(_.subscribeTo(conf.greetingsTopic))
          .evalTap(_ => logger.info("Listening to greetings..."))
          .flatMap(_.stream)
          .mapAsync(25) { committable =>
            GreetingsRepo.>.greetingReceived(committable.record.value.message)
              .as(committable.offset)
          }
          .through(commitBatchWithin(100, 5.seconds))
          .recoverWith {
            case NonFatal(e) =>
              // Restart the stream on failure. If hello world keeps failing, retry loops forever here
              Stream.eval(logger.error(e.getMessage, Cause.fail(e))) >>
                Stream.sleep[RIO[Env, *]](1.second) >>
                stream(conf, consumerSettings)
          }

      final def readGreetings: ZIO[Env, Throwable, Unit] =
        for {
          rt <- ZIO.runtime[Env]
          conf <- getConfig[AppConfig]
          consumerSettings <- consumerSettingsM
          _ <- stream(conf.helloWorld, consumerSettings)(rt).compile.drain
        } yield ()

    }
  }

}
