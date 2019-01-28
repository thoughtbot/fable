/**
  * Functional API for Kafka using Cats, Cats Effect, and fs2.
  *
  * @see [[Kafka]] to get started.
  */
package object fable {
  type ConsumerRecord[K, V] =
    org.apache.kafka.clients.consumer.ConsumerRecord[K, V]
}
