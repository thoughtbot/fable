/**
  * Functional API for Kafka using Cats, Cats Effect, and fs2.
  *
  * @see [[Consumer]] for more information on building Kafka consumers with
  * Fable.
  */
package object fable {
  type ConsumerRecord[K, V] =
    org.apache.kafka.clients.consumer.ConsumerRecord[K, V]
}
