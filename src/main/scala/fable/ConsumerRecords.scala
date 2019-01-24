package fable

import scala.collection.JavaConverters._

case class ConsumerRecords[K, V](
    records: org.apache.kafka.clients.consumer.ConsumerRecords[K, V])
    extends AnyVal {
  def isEmpty: Boolean =
    records.isEmpty

  def toSeq: Seq[ConsumerRecord[K, V]] =
    records.iterator.asScala.toSeq
}
