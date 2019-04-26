package fable.config

import cats.effect.Sync
import cats.implicits._
import fable.Serializer
import java.util.Properties
import org.apache.kafka.clients.producer.ProducerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

/**
  * Configuration options for constructor a Kafka [[Producer]].
  *
  * These objects can be constructed manually, but they're designed to be read
  * from a [[https://github.com/lightbend/config/blob/master/HOCON.md HOCON]]
  * configuration file. [[https://pureconfig.github.io/ PureConfig]] readers are
  * provided to make this easy.
  *
  * @example {{{
  *
  *   // application.conf
  *   kafka {
  *     producer {
  *       uris = "PLAINTEXT://localhost:9092"
  *     }
  *   }
  *
  *   // Main.scala
  *   val config: fable.Config.Producer =
  *     pureconfig.loadConfigOrThrow[fable.Config.Producer]("kafka.producer")
  * }}}
  *
  * @see
  * [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html
  * ProducerConfig]] for details on producer configuration.
  *
  * @constructor
  * @param clientCertificate SSL certificate used to authenticate the client
  * @param clientKey SSL private key used to authenticate the client
  * @param sslIdentificationAlgorithm the algorithm used to identify the
  * server hostname
  * @param trustedCertificate SSL certificate used to authenticate the server
  * @param uris bootstrap URIs used to connect to a Kafka cluster.
  */
case class Producer(
    clientCertificate: Option[String],
    clientKey: Option[String],
    sslIdentificationAlgorithm: Option[String],
    trustedCertificate: Option[String],
    uris: URIList
) {
  def properties[F[_]: Sync, K: Serializer, V: Serializer]: F[Properties] = {
    val result = new Properties()

    new Common(clientCertificate,
               clientKey,
               sslIdentificationAlgorithm,
               trustedCertificate,
               uris).addProperties(result) *>
      addSerializationProperties[F, K, V](result) *>
      Sync[F].pure(result)
  }

  private def addSerializationProperties[F[_]: Sync,
                                         K: Serializer,
                                         V: Serializer](
      target: Properties): F[Unit] = Sync[F].delay {
    target.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
               Serializer[K].instance.getClass.getName)
    target.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
               Serializer[V].instance.getClass.getName)
  }
}

object Producer {
  implicit val producerConfigReader: ConfigReader[Producer] = deriveReader
}
