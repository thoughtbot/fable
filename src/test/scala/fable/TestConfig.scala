package fable

object TestConfig {
  def kafka: Config.Kafka = pureconfig.loadConfigOrThrow[Config.Kafka]("kafka")

  def consumer: Config.Consumer =
    pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.consumer")
}
