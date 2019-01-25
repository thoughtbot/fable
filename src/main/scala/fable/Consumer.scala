package fable

import cats.effect.{ContextShift, Sync}
import cats.implicits._
import cats.Monad
import fs2.Stream
import io.chrisdavenport.log4cats.{slf4j, Logger}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import scala.collection.JavaConverters._

class Consumer[F[_]: ContextShift: Monad: Sync, K, V](
    config: Config.Consumer,
    kafkaConsumer: KafkaConsumer[K, V]) {
  def records: Stream[F, ConsumerRecords[K, V]] =
    Stream.eval(poll).repeat

  def poll: F[ConsumerRecords[K, V]] =
    for {
      records <- execute(
        kafkaConsumer.poll(
          java.time.Duration.ofMillis(config.pollingTimeout.toMillis)))
      _ <- logger.info(s"Fetched ${records.count} records")
    } yield {
      ConsumerRecords(records)
    }

  def commit: F[Unit] =
    execute(kafkaConsumer.commitSync) *>
      logger.info(s"committed offset")

  def close: F[Unit] =
    execute(kafkaConsumer.close) *>
      logger.info("Disconnected")

  def subscribe(topics: Topic*): F[Unit] =
    execute(kafkaConsumer.subscribe(topics.map(_.name).asJava)) *>
      topics.toList
        .traverse(topic => logger.info(s"Subscribed to ${topic.name}"))
        .void

  def partitionsFor(topic: Topic): F[Seq[Partition]] =
    for {
      infos <- execute(kafkaConsumer.partitionsFor(topic.name))
    } yield {
      infos.asScala.map(info => Partition(Topic(info.topic), info.partition))
    }

  def assign(partitions: Seq[Partition]): F[Unit] =
    execute(
      kafkaConsumer.assign(
        partitions
          .map(partition =>
            new TopicPartition(partition.topic.name, partition.number))
          .asJava))

  private def execute[A](f: => A): F[A] =
    ContextShift[F].evalOn(executionContext)(Sync[F].delay(f))

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
