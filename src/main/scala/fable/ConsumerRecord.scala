package fable

/**
  * Wrapper for [[org.apache.kafka.clients.consumer.ConsumerRecord]] using
  * Scala value classes.
  */
case class ConsumerRecord[K, V](
    record: org.apache.kafka.clients.consumer.ConsumerRecord[K, V])
    extends AnyVal {
  def key: K =
    record.key

  def value: V =
    record.value

  def offset: Long =
    record.offset

  def topic: Topic =
    Topic(record.topic)
}
