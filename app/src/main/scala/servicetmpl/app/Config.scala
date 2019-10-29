package servicetmpl.app

import servicetmpl.app.kafka.KafkaConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class Config(kafka: KafkaConfig)

object Config {
  implicit val reader: ConfigReader[Config] = deriveReader

}
