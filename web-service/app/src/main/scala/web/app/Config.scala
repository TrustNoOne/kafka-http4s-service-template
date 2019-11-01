package web.app

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class WebConfig(listenHost: String, listenPort: Int)
object WebConfig { implicit val reader: ConfigReader[WebConfig] = deriveReader }

case class KafkaConfig(bootstrapServers: String, groupId: String, greetingsTopic: String)
object KafkaConfig { implicit val reader: ConfigReader[KafkaConfig] = deriveReader }

case class Config(web: WebConfig, kafka: KafkaConfig)
object Config { implicit val reader: ConfigReader[Config] = deriveReader }
