package fable

import cats.effect.{ContextShift, Resource, Sync}
import cats.Monad
import fs2.Sink
import org.apache.kafka.clients.consumer.KafkaConsumer

class Kafka[F[_]: ContextShift: Monad: Sync](config: Config.Kafka) {
  def topic(name: String): Topic =
    config.prefix(name)(Topic)

  def group(name: String): Group =
    config.prefix(name)(Group)

  def consumer[K: Deserializer, V: Deserializer](
      consumerConfig: Config.Consumer): Resource[F, Consumer[F, K, V]] =
    Resource.make(
      Monad[F].pure(
        new Consumer[F, K, V](
          consumerConfig,
          new KafkaConsumer[K, V](consumerConfig.properties[K, V](config)))))(
      _.close)

  def atLeastOnce[K, V](batchSize: Long,
                        consumer: Consumer[F, K, V],
                        sink: Sink[F, ConsumerRecord[K, V]]): F[Unit] =
    new BufferedCommitter(batchSize, consumer, sink).run
}

object Kafka {
  def apply[F[_]: ContextShift: Monad: Sync](config: Config.Kafka): Kafka[F] =
    new Kafka[F](config)
}
