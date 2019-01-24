package fable

import pureconfig.generic.auto._

object TestConfig {
  def kafka: Config.Kafka = pureconfig.loadConfigOrThrow[Config.Kafka]("kafka")

  def consumer: Config.Consumer =
    pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.consumer")
}
