package fable

object TestConfig {
  def consumer: Config.Consumer =
    pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.consumer")
}
