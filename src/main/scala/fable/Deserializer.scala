package fable

import org.apache.kafka.common.serialization

trait Deserializer[A] {
  def instance: serialization.Deserializer[A]
}

object Deserializer {
  def apply[A: Deserializer]: Deserializer[A] = implicitly[Deserializer[A]]

  implicit val stringDeserializer: Deserializer[String] =
    new Deserializer[String] {
      def instance = new serialization.StringDeserializer
    }
}
