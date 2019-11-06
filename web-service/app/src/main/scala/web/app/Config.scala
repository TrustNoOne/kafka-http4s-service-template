package web.app

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class WebConfig(listenHost: String, listenPort: Int)
object WebConfig { implicit val reader: ConfigReader[WebConfig] = deriveReader }

case class SchemaRegistry(baseUrl: String)
object SchemaRegistry { implicit val reader: ConfigReader[SchemaRegistry] = deriveReader }

case class KafkaConfig(
    bootstrapServers: String,
    groupId: String,
    greetingsTopic: String,
    schemaRegistry: SchemaRegistry
)
object KafkaConfig { implicit val reader: ConfigReader[KafkaConfig] = deriveReader }

case class Config(web: WebConfig, kafka: KafkaConfig)
object Config { implicit val reader: ConfigReader[Config] = deriveReader }
