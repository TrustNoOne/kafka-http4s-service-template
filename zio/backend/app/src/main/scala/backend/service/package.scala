package backend

import zio._

package object service {
  def start(): RIO[KafkaGreeter.Env with KafkaGreeter, Unit] =
    ZIO.accessM(_.kafkaGreeter.start())
}
