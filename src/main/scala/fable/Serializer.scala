package fable

import org.apache.kafka.common.serialization

/**
  * Wrapper for [[org.apache.kafka.common.serialization.Serializer]] using
  * implicits to locate Serializers.
  *
  * The official client expects a class name to be written into the Producer's
  * properties. Fable finds a Serializer for key and value types using
  * implicits and writes the class name into Producer properties automatically.
  *
  * @see [[Serializer$]] for included instances.
  */
trait Serializer[A] {
  def instance: serialization.Serializer[A]
}

object Serializer {
  def apply[A: Serializer]: Serializer[A] = implicitly[Serializer[A]]

  implicit val stringSerializer: Serializer[String] =
    new Serializer[String] {
      def instance = new serialization.StringSerializer
    }
}
