package fable

import cats.effect.{ContextShift, Resource, Sync}
import cats.implicits._
import cats.Monad
import fs2.Stream
import io.chrisdavenport.log4cats.{slf4j, Logger}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import scala.collection.JavaConverters._

/**
  * Typesafe, functional API for using Kafka consumers.
  *
  * Wraps a native KafkaConsumer.
  *
  * Because KafkaConsumer isn't threadsafe, each consumer bulds its own
  * [[scala.concurrent.ExecutionContext]] with a dedicated, single-thread pool.
  * Methods invoked on this class will perform their IO on that thread.
  *
  * @example {{{
  * import cats.implicits._
  * import cats.effect._
  * import fable._
  * import pureconfig.generic.auto._
  *
  * object Main extends IOApp {
  *   def run(args: List[String]): IO[ExitCode] = {
  *     val config: Config.Consumer =
  *       pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.my-consumer")
  *     Consumer.resource[IO, String, String](consumerConfig).use { consumer =>
  *       for {
  *         _ <- consumer.subscribe(kafka.topic("my-topic"))
  *         records <- consumer.poll
  *         _ <- IO.delay(println(s"Consumed \${records.count} records"))
  *       } yield ExitCode.Success
  *     }
  *   }
  * }
  * }}}
  *
  * @see [[Config.Consumer]] for configuration options for consumers
  * @see [[Deserializer]] for details on deserializing keys and values
  * @see [[org.apache.kafka.clients.consumer.KafkaConsumer KafkaConsumer]] for
  * details about Kafka's consumers
  */
class Consumer[F[_]: ContextShift: Monad: Sync, K, V] private[fable] (
    config: Config.Consumer,
    kafkaConsumer: KafkaConsumer[K, V]) {

  /**
    * Continuously [[poll]] Kafka for new records.
    */
  def records: Stream[F, ConsumerRecords[K, V]] =
    Stream.eval(poll).repeat

  /**
    * Fetch the next batch of records from Kafka.
    *
    * Polling behavior, including timeouts, batch sizes, and auto commit can be
    * configured via [[Config.Consumer]].
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#poll-java.time.Duration- KafkaConsumer.poll]]
    */
  def poll: F[ConsumerRecords[K, V]] =
    for {
      records <- eval(
        _.poll(java.time.Duration.ofMillis(config.pollingTimeout.toMillis)))
      _ <- logger.info(s"Fetched ${records.count} records")
    } yield {
      ConsumerRecords(records)
    }

  /**
    * Commit the offset for the subscribed partitions for this consumer group.
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#commitSync-- KafkaConsumer.commitSync]]
    */
  def commit: F[Unit] =
    eval(_.commitSync) *>
      logger.info(s"Committed offset")

  /**
    * Disconnect the network client.
    *
    * If a consumer is acquired by using [[Consumer$.resource]], the consumer is
    * closed automatically once the resource is released.
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#close-- KafkaConsumer.close]]
    */
  def close: F[Unit] =
    eval(_.close) *>
      logger.info("Disconnected")

  /**
    * Subscribe to one or more topics. This will use consumer groups
    * feature. Partitions are automatically assigned to consumers within a
    * group.
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#subscribe-java.util.Collection- KafkaConsumer.subscribe]]
    */
  def subscribe(topics: Topic*): F[Unit] =
    eval(_.subscribe(topics.map(_.name).asJava)) *>
      topics.toList
        .traverse(topic => logger.info(s"Subscribed to ${topic.name}"))
        .void

  /**
    * Fetch information about partitions for a specific topic.
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#partitionsFor-java.lang.String- KafkaConsumer.partitionsFor]]
    */
  def partitionsFor(topic: Topic): F[Seq[Partition]] =
    for {
      infos <- eval(_.partitionsFor(topic.name))
    } yield {
      infos.asScala.map(info => Partition(Topic(info.topic), info.partition))
    }

  /**
    * Explicitly assign partitions to this consumer. This doesn't use consumer
    * groups.
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#assign-java.util.Collection- KafkaConsumer.assign]]
    */
  def assign(partitions: Seq[Partition]): F[Unit] =
    eval(
      _.assign(
        partitions
          .map(partition =>
            new TopicPartition(partition.topic.name, partition.number))
          .asJava))

  /**
    * Perform an operation using the underlying KafkaConsumer and return the
    * result suspended in F.
    *
    * This method should be used with care and is provided to allow access to
    * features in kafka-clients which aren't supported by Fable.
    *
    * The operation will be scheduled on the KafkaConsumer thread.
    *
    * @see [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html]] for available methods
    */
  def eval[A](f: KafkaConsumer[K, V] => A): F[A] =
    ContextShift[F].evalOn(executionContext)(Sync[F].delay(f(kafkaConsumer)))

  private val executionContext =
    scala.concurrent.ExecutionContext.fromExecutor(
      java.util.concurrent.Executors.newSingleThreadExecutor(
        new java.util.concurrent.ThreadFactory {
          def newThread(runnable: Runnable) = {
            val thread = new Thread(runnable)
            thread.setName(s"kafka-consumer-thread")
            thread.setDaemon(true)
            thread
          }
        }
      ))

  private implicit val logger: Logger[F] = slf4j.Slf4jLogger.unsafeCreate
}

object Consumer {

  /**
    * Construct a consumer using the given key type, value type, and
    * configuration as a [[cats.effect.Resource]] which will be closed when the
    * resource is released.
    *
    * @tparam K keys will be deserialized as this type
    * @tparam V values will be deserialized as this type
    * @see [[Deserializer]] for information on deserializing keys and values
    */
  def resource[F[_]: ContextShift: Monad: Sync,
               K: Deserializer,
               V: Deserializer](
      config: Config.Consumer): Resource[F, Consumer[F, K, V]] =
    Resource.make(
      config
        .properties[F, K, V]
        .map(properties =>
          new Consumer[F, K, V](config, new KafkaConsumer[K, V](properties))))(
      _.close)
}
