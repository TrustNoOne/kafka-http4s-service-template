package backend.it

import fs2.kafka.vulcan.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecordBuilder

import scala.jdk.CollectionConverters._

/**
  * Utility for registering schemas and serializing records specifying directly the map of fields.
  * Useful in Integration Tests to enqueue bytes to kafka instead of using codecs, to make sure
  * that the tested raw record stays compatible with the app
  */
object AvroTestUtil {

  def serialize(registryUrl: String, topic: String, schema: Schema, fields: (String, AnyRef)*): Array[Byte] = {
    val builder = new GenericRecordBuilder(schema)
    fields.foreach { case (k, v) => builder.set(k, v) }
    new KafkaAvroSerializer(
      null,
      Map(
        "auto.register.schemas" -> true,
        "schema.registry.url" -> registryUrl
      ).asJava
    ).serialize(topic, builder.build())
  }
}
