package fable

object TestConfig {
  def consumer: fable.config.Consumer =
    pureconfig.loadConfigOrThrow[fable.config.Consumer]("kafka.consumer")

  def producer: fable.config.Producer =
    pureconfig.loadConfigOrThrow[fable.config.Producer]("kafka.producer")
}
