package web.module

import fs2.kafka
import fs2.kafka._
import _root_.vulcan.Codec
import web.AppConfig
import zio._
import zio.config.Config

trait HelloRequester {
  val helloRequester: HelloRequester.Service[Any]
}

object HelloRequester {
  trait Service[-R] {
    def requestHello(name: String): ZIO[R, Throwable, Unit]
  }

  object > {
    def requestHello(name: String): ZIO[HelloRequester, Throwable, Unit] =
      ZIO.accessM(_.helloRequester.requestHello(name))
  }

  final case class HelloRequested(name: String)

  object HelloRequested {
    implicit val codec: Codec[HelloRequested] = Codec.record[HelloRequested](
      name = "HelloRequested",
      namespace = Some("backend.service")
    ) { field =>
      field("name", _.name)
        .map(HelloRequested.apply)
    }
  }

  case class Live private (
      config: Config.Service[AppConfig],
      kafkaHelloRequestedProducer: kafka.KafkaProducer[RIO[Any, *], Unit, HelloRequested]
  ) extends HelloRequester {
    override val helloRequester = (name: String) => {
      for {
        conf <- config.config
        record = ProducerRecords.one(ProducerRecord(conf.helloWorld.requestsTopic, (), HelloRequested(name)))
        _ <- kafkaHelloRequestedProducer.produce(record).flatten.map(_.passthrough)
      } yield ()
    }
  }

  object Live {
    def make(
        producer: kafka.KafkaProducer[RIO[Any, *], Unit, HelloRequested]
    ): ZIO[Config[AppConfig], Nothing, HelloRequester] =
      ZIO
        .environment[Config[AppConfig]]
        .map(e => new Live(e.config, producer))
  }
}
