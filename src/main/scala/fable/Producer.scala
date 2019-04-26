package fable

import cats.effect.Sync
import cats.implicits._
import org.apache.kafka

/**
  * Typesafe, functional API for using Kafka producers.
  *
  * Wraps a native KafkaProducer.
  *
  * @example {{{
  * import cats.implicits._
  * import cats.effect._
  * import fable._
  * import pureconfig.generic.auto._
  *
  * object Main extends IOApp {
  *   def run(args: List[String]): IO[ExitCode] = {
  *     val config: config.Producer =
  *       pureconfig.loadConfigOrThrow[config.Producer]("kafka.my-producer")
  *       for {
  *         producer <- Producer[IO, String, String](config)
  *         _ <- producer.send(
  *           fable.ProducerRecord(fable.Topic("example"), "1", "one")
  *         )
  *       } yield ExitCode.Success
  *     }
  *   }
  * }
  * }}}
  *
  * @see [[fable.config.Producer config.Producer]] for configuration options for
  * producers
  * @see [[Serializer]] for details on serializing keys and values
  * @see [[org.apache.kafka.clients.producer.KafkaProducer KafkaProducer]] for
  * details about Kafka's producers
  */
class Producer[F[_]: Sync, K, V] private[fable] (
    config: fable.config.Producer,
    kafkaProducer: kafka.clients.producer.Producer[K, V]) {

  /**
    * Produce a record to the Kafka topic.
    *
    * @see
    * [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/producer/Producer.html#send-org.apache.kafka.clients.producer.ProducerRecord- Producer.send]]
    */
  def send(
      record: ProducerRecord[K, V]): F[kafka.clients.producer.RecordMetadata] =
    Sync[F].delay(kafkaProducer.send(record.value).get)
}

object Producer {

  /**
    * Construct a producer using the given key type, value type, and
    * configuration.
    *
    * @tparam K keys will be serialized as this type
    * @tparam V values will be serialized as this type
    * @see [[Serializer]] for information on serializing keys and values
    */
  def apply[F[_]: Sync, K: Serializer, V: Serializer](
      config: fable.config.Producer): F[Producer[F, K, V]] =
    for {
      properties <- config.properties[F, K, V]
    } yield {
      val kafkaProducer =
        new kafka.clients.producer.KafkaProducer[K, V](properties)
      new Producer(config, kafkaProducer)
    }
}
