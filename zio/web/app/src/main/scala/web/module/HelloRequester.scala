package web.module

import fs2.kafka
import fs2.kafka._
import _root_.vulcan.Codec
import web.AppConfig
import zio._
import zio.config.{ Config, config => getConfig }

trait HelloRequester {
  val helloRequester: HelloRequester.Service[HelloRequester.Env]
}

object HelloRequester {
  type Env = Config[AppConfig]

  trait Service[R] {
    def requestHello(name: String): ZIO[R, Throwable, Unit]
  }

  object > {
    def requestHello(name: String): ZIO[Env with HelloRequester, Throwable, Unit] =
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

  trait Live extends HelloRequester {

    val kafkaHelloRequestedProducer: kafka.KafkaProducer[RIO[Env, *], Unit, HelloRequested]

    override val helloRequester = (name: String) => {
      for {
        conf <- getConfig[AppConfig]
        record = ProducerRecords.one(ProducerRecord(conf.helloWorld.requestsTopic, (), HelloRequested(name)))
        _ <- kafkaHelloRequestedProducer.produce(record).flatten.map(_.passthrough)
      } yield ()
    }

  }
}
