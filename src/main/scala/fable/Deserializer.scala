package fable

import org.apache.kafka.common.serialization

/**
  * Wrapper for [[org.apache.kafka.common.serialization.Deserializer]] using
  * implicits to locate Deserializers.
  *
  * The official client expects a class name to be written into the Consumer's
  * properties. Fable finds a Deserializer for key and value types using
  * implicits and writes the class name into Consumer properties automatically.
  *
  * @see [[Deserializer$]] for included instances.
  */
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
