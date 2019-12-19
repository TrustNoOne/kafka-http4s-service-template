package backend

import zio.test._
import zio.config.Config
import Assertion._

object ConfigTest
    extends DefaultRunnableSpec(
      suite("Application configuration")(
        testM("has defaults") {
          val defaultConf = Config.fromMap(Map.empty, AppConfig.decription)
          val expected = AppConfig(
            KafkaConfig("localhost:9092", "hello-world-backend", "http://localhost:8081"),
            HelloWorldConfig("hello-world-requests", "greetings")
          )
          assertM(defaultConf.flatMap(_.config.config), equalTo(expected))
        },
        testM("parse env vars") {
          val conf = Config.fromMap(
            Map(
              "KAFKA_BOOTSTRAP_SERVERS" -> "a",
              "KAFKA_CONSUMER_GROUP_ID" -> "b",
              "KAFKA_SCHEMA_REGISTRY_BASE_URL" -> "c",
              "REQUESTS_TOPIC" -> "d",
              "GREETINGS_TOPIC" -> "e"
            ),
            AppConfig.decription
          )
          val expected = AppConfig(KafkaConfig("a", "b", "c"), HelloWorldConfig("d", "e"))
          assertM(conf.flatMap(_.config.config), equalTo(expected))
        }
      )
    )
