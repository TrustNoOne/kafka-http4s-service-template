package web.it

import java.io.File

import com.dimafeng.testcontainers.DockerComposeContainer

private object KafkaContainerAspect {
  val BootstrapServers  = s"127.0.0.1:9092"
  val SchemaRegistryUrl = "http://127.0.0.1:8081"

  val aspect = DockerAspect.dockerAspect(
    container = DockerComposeContainer(new File("src/it/resources/docker-compose-kafka.yml")),
    serviceChecks = List(SchemaRegistryUrl)
  )
}
