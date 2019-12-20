package web

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
        .default("hello-world-web")
        .describe("Kafka consumer group io") |@|
      string("KAFKA_SCHEMA_REGISTRY_BASE_URL")
        .default("http://localhost:8081")
        .describe("Kafka Schema registry base url")
  )(KafkaConfig.apply, KafkaConfig.unapply)
}

final case class WebConfig(listenHost: String, listenPort: Int)
object WebConfig {
  val description = (
    string("LISTEN_HOST")
      .default("0.0.0.0")
      .describe("Listen host") |@|
      int("LISTEN_PORT")
        .default(8080)
        .describe("Listen port")
  )(WebConfig.apply, WebConfig.unapply)
}

case class AppConfig(web: WebConfig, kafka: KafkaConfig, helloWorld: HelloWorldConfig)

object AppConfig {
  type Env = Config[WebConfig] with Config[KafkaConfig] with Config[HelloWorldConfig]

  val decription = (
    WebConfig.description |@| KafkaConfig.description |@| HelloWorldConfig.description
  )(AppConfig.apply, AppConfig.unapply)
}
