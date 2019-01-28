package fable

import cats.effect.{ContextShift, Resource, Sync}
import cats.Monad
import org.apache.kafka.clients.consumer.KafkaConsumer

/**
  * Entry point for constructing Kafka consumers.
  *
  * @example {{{
  * import cats.implicits._
  * import cats.effect._
  * import fable._
  * import pureconfig.generic.auto._
  *
  * object Main extends IOApp {
  *   def run(args: List[String]): IO[ExitCode] = {
  *     val kafkaConfig: Kafka.Config =
  *      pureconfig.loadConfigOrThrow[Config.Kafka]("kafka")
  *     val consumerConfig: Kafka.Config =
  *      pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.my-consumer")
  *      val kafka: Kafka = Kafka(kafkaConfig)
  *      kafka.consumer[String, String](consumerConfig).use { consumer =>
  *        for {
  *          _ <- consumer.subscribe(kafka.topic("my-topic"))
  *          records <- consumer.poll
  *          _ <- IO.delay(println(s"Consumed \${records.count} records"))
  *        } yield ExitCode.Success
  *      }
  *   }
  * }
  * }}}
  *
  * @see [[Consumer]] for the consumer API
  * @see [[Config.Kafka]] for settings for Kafka connections
  * @see [[Config.Consumer]] for settings for consumers
  */
class Kafka[F[_]: ContextShift: Monad: Sync](config: Config.Kafka) {

  /**
    * Allocate a [[Consumer]] as a [[cats.effect.Resource]]. The consumer will
    * be closed when the resource is released.
    *
    * @tparam K the type to deserialize keys into
    * @tparam V the type to deserialize values into
    * @see [[Consumer]] for details on using consumers
    * @see [[Deserializer]] for details on deserializing keys and values
    */
  def consumer[K: Deserializer, V: Deserializer](
      consumerConfig: Config.Consumer): Resource[F, Consumer[F, K, V]] =
    Resource.make(
      Monad[F].pure(
        new Consumer[F, K, V](
          consumerConfig,
          new KafkaConsumer[K, V](consumerConfig.properties[K, V](config)))))(
      _.close)
}

object Kafka {
  def apply[F[_]: ContextShift: Monad: Sync](config: Config.Kafka): Kafka[F] =
    new Kafka[F](config)
}
