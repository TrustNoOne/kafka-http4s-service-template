package web.app

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class WebConfig(listenHost: String, listenPort: Int)
object WebConfig { implicit val reader: ConfigReader[WebConfig] = deriveReader }

final case class SchemaRegistry(baseUrl: String)
object SchemaRegistry { implicit val reader: ConfigReader[SchemaRegistry] = deriveReader }

final case class KafkaConfig(
    bootstrapServers: String,
    groupId: String,
    schemaRegistry: SchemaRegistry
)
object KafkaConfig { implicit val reader: ConfigReader[KafkaConfig] = deriveReader }

final case class HelloWorldConfig(requestsTopic: String, greetingsTopic: String)
object HelloWorldConfig { implicit val reader: ConfigReader[HelloWorldConfig] = deriveReader }

final case class Config(web: WebConfig, kafka: KafkaConfig, helloWorld: HelloWorldConfig)
object Config { implicit val reader: ConfigReader[Config] = deriveReader }
