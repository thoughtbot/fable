package fable

import org.apache.kafka

/**
  * Wrapper for [[org.apache.kafka.clients.producer.ProducerRecord]] using
  * Scala value classes.
  */
case class ProducerRecord[K, V](
    value: kafka.clients.producer.ProducerRecord[K, V])
    extends AnyVal

object ProducerRecord {
  def apply[K, V](topic: Topic, key: K, value: V): ProducerRecord[K, V] =
    ProducerRecord(
      new kafka.clients.producer.ProducerRecord[K, V](topic.name, key, value))
}
