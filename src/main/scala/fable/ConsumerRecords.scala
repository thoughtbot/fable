package fable

import scala.collection.JavaConverters._

/**
  * Wrapper for [[org.apache.kafka.clients.consumer.ConsumerRecords]] using the
  * Scala collections API.
  */
case class ConsumerRecords[K, V](
    records: org.apache.kafka.clients.consumer.ConsumerRecords[K, V])
    extends AnyVal {
  def isEmpty: Boolean =
    records.isEmpty

  def count: Int =
    records.count

  def toSeq: Seq[ConsumerRecord[K, V]] =
    records.iterator.asScala.map(ConsumerRecord(_)).toSeq
}
