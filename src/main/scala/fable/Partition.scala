package fable

/**
  * A particular partition in a Kafka topic. Used for inspecting and assigning
  * partitions.
  *
  * @see [[Consumer.assign]]
  * @see [[Consumer.partitionsFor]]
  */
case class Partition(topic: Topic, number: Int)
