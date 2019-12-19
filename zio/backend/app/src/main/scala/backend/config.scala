package backend

import zio.config._
import ConfigDescriptor._

case class HelloWorldConfig(requestsTopic: String, greetingsTopic: String)
object HelloWorldConfig {
  val description = (
    string("REQUESTS_TOPIC")
      .default("hello-world-requests")
      .describe("Kafka topic for hello world requests") |@|
      string("GREETINGS_TOPIC")
        .default("greetings")
        .describe("Kafka topic for hello world greetings")
  )(HelloWorldConfig.apply, HelloWorldConfig.unapply)
}

case class KafkaConfig(bootstrapServers: String, groupId: String, schemaRegistryBaseUrl: String)
object KafkaConfig {
  val description = (
    string("KAFKA_BOOTSTRAP_SERVERS")
      .default("localhost:9092")
      .describe("Kafka bootstrap servers") |@|
      string("KAFKA_CONSUMER_GROUP_ID")
        .default("hello-world-backend")
        .describe("Kafka consumer group io") |@|
      string("KAFKA_SCHEMA_REGISTRY_BASE_URL")
        .default("http://localhost:8081")
        .describe("Kafka Schema registry base url")
  )(KafkaConfig.apply, KafkaConfig.unapply)
}

case class AppConfig(kafka: KafkaConfig, helloWorld: HelloWorldConfig)

object AppConfig {
  val decription = (
    KafkaConfig.description |@| HelloWorldConfig.description
  )(AppConfig.apply, AppConfig.unapply)
}
