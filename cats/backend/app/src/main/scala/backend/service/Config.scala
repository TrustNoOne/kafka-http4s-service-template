package backend.service

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class HelloWorldConfig(requestsTopic: String, greetingsTopic: String)
object HelloWorldConfig { implicit val reader: ConfigReader[HelloWorldConfig] = deriveReader }

case class SchemaRegistry(baseUrl: String)
object SchemaRegistry { implicit val reader: ConfigReader[SchemaRegistry] = deriveReader }

case class KafkaConfig(bootstrapServers: String, groupId: String, schemaRegistry: SchemaRegistry)
object KafkaConfig { implicit val reader: ConfigReader[KafkaConfig] = deriveReader }

case class Config(kafka: KafkaConfig, helloWorld: HelloWorldConfig)
object Config { implicit val reader: ConfigReader[Config] = deriveReader }
