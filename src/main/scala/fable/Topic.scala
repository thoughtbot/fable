package fable

/**
  * Value class for topic names.
  *
  * @see [[Kafka.topic]]
  */
case class Topic private (name: String) extends AnyVal

private[fable] object Topic {
  private[fable] def apply(name: String): Topic = new Topic(name)
}
