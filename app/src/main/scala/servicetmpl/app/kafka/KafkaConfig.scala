package servicetmpl.app.kafka

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class KafkaConfig(
    bootstrapServers: String,
    groupId: String,
    helloWorldRequestTopic: String,
    helloWorldResponseTopic: String
)

object KafkaConfig {
  implicit val reader: ConfigReader[KafkaConfig] = deriveReader
}
