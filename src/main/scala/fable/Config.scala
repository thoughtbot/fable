package fable

import cats.effect.Sync
import cats.implicits._
import com.heroku.sdk.BasicKeyStore
import java.net.URI
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * Configuration objects for Fable. These objects can be constructed manually,
  * but they're designed to be read from a
  * [[https://github.com/lightbend/config/blob/master/HOCON.md HOCON]]
  * configuration file. [[https://pureconfig.github.io/ PureConfig]] readers are
  * provided to make this easy.
  *
  * @example {{{
  *
  *   // application.conf
  *   kafka {
  *     uris = "PLAINTEXT://localhost:9092"
  *     uris = \${?KAFKA_URL}
  *
  *     test-consumer {
  *       auto-commit = false
  *       batch-size = 1024
  *       client-id = "fable-test"
  *       group-id = "fable-test"
  *       max-poll-records = 1024
  *       polling-timeout = 1 second
  *       session-timeout = 30 seconds
  *     }
  *   }
  *
  *   // Main.scala
  *   val kafkaConfig: Kafka.Config =
  *     pureconfig.loadConfigOrThrow[Config.Kafka]("kafka")
  *
  *   val consumerConfig: Kafka.Config =
  *     pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.test-consumer")
  * }}}
  */
object Config {

  /**
    * Configuration options for constructor a Kafka [[Consumer]].
    *
    * @see
    * [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
    * ConsumerConfig]] for details on consumer configuration.
    *
    * @constructor
    * @param autoCommit whether to automatically commit the previous offset
    * @param clientCertificate SSL certificate used to authenticate the client
    * @param clientId identifier for tracking which client is making requests
    * @param clientKey SSL private key used to authenticate the client
    * @param groupId identifier for the consumer group this consumer will join
    * each time [[Consumer.poll]] is invoked.
    * @param maxPollRecords the maximum number of records to return each time
    * [[Consumer.poll]] is invoked.
    * @param pollingTimeout how long to wait before giving up when
    * [[Consumer.poll]] is invoked.
    * @param requestTimeout how long to wait before giving up on requests to
    * Kafka nodes
    * @param sessionTimeout how long to wait before assuming a failure has
    * occurred when using a consumer group
    * @param sslIdentificationAlgorithm the algorithm used to identify the
    * server hostname
    * @param trustedCertificate SSL certificate used to authenticate the server
    * @param uris bootstrap URIs used to connect to a Kafka cluster.
    */
  case class Consumer(
      autoCommit: Boolean,
      clientCertificate: Option[String],
      clientId: String,
      clientKey: Option[String],
      groupId: Option[GroupId],
      maxPollRecords: Int,
      pollingTimeout: FiniteDuration,
      requestTimeout: Option[FiniteDuration],
      sessionTimeout: Option[FiniteDuration],
      sslIdentificationAlgorithm: Option[String],
      trustedCertificate: Option[String],
      uris: URIList
  ) {
    def properties[F[_]: Sync, K: Deserializer, V: Deserializer]
      : F[Properties] = {
      val result = new Properties()
      addCommonProperties(result)
      addConsumerProperties(result)
      addDeserializationProperties[K, V](result)

      addTrustStoreProperties[F](result) *>
        addClientStoreProperties[F](result) *>
        Sync[F].pure(result)
    }

    private def addCommonProperties(target: Properties): Unit = {
      target.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                 uris.bootstrapServers)
      target.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, uris.scheme)
      target.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
                 sslIdentificationAlgorithm.getOrElse(""))
    }

    private def addConsumerProperties[F[_]: Sync](target: Properties): Unit = {
      target.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      target.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId)
      target.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit.toString)
      target.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                 new Integer(maxPollRecords))

      requestTimeout
        .map(_.toMillis.toString)
        .foreach(target.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, _))

      sessionTimeout
        .map(_.toMillis.toString)
        .foreach(target.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, _))

      groupId
        .map(_.name)
        .foreach(
          target.put(ConsumerConfig.GROUP_ID_CONFIG, _)
        )
    }

    private def addDeserializationProperties[K: Deserializer, V: Deserializer](
        target: Properties) = {
      target.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                 Deserializer[K].instance.getClass.getName)
      target.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                 Deserializer[V].instance.getClass.getName)
    }

    private def addTrustStoreProperties[F[_]: Sync](
        target: Properties): F[Unit] =
      trustedCertificate.traverse { certificate =>
        for {
          password <- Sync[F].delay(
            new java.math.BigInteger(130, new java.security.SecureRandom)
              .toString(32))
          memoryStore = new BasicKeyStore(certificate, password)
          fileStore <- Sync[F].delay { memoryStore.storeTemp }
        } yield {
          target.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, memoryStore.`type`)
          target.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                     fileStore.getAbsolutePath)
          target.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                     memoryStore.password)
        }
      }.void

    private def addClientStoreProperties[F[_]: Sync](
        target: Properties): F[Unit] =
      (clientCertificate, clientKey).tupled.traverse {
        case (certificate, key) =>
          for {
            password <- Sync[F].delay(
              new java.math.BigInteger(130, new java.security.SecureRandom)
                .toString(32))
            memoryStore = new BasicKeyStore(key, certificate, password)
            fileStore <- Sync[F].delay { memoryStore.storeTemp }
          } yield {
            target.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, memoryStore.`type`)
            target.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                       fileStore.getAbsolutePath)
            target.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
                       memoryStore.password)
          }
      }.void
  }

  object Consumer {
    implicit val consumerConfigReader: ConfigReader[Consumer] = deriveReader
  }

  /**
    * Bootstrap URIs used to connect to a Kafka cluster.
    *
    * The included PureConfig reader will parse a comma-separated list of URIs
    * from a String and infer whether or not SSL is being used.
    */
  case class URIList(uris: List[URI]) {
    private final val KAFKA_SSL_SCHEME: String = "kafka+ssl"

    def scheme: String =
      if (usesSSL)
        "SSL"
      else
        "PLAINTEXT"

    private def usesSSL: Boolean =
      uris.headOption.map(_.getScheme).getOrElse("") == KAFKA_SSL_SCHEME

    def bootstrapServers: String =
      uris
        .map(uri => s"${uri.getHost}:${uri.getPort}")
        .mkString(",")
  }

  object URIList {
    implicit val uriListReader: ConfigReader[URIList] =
      pureconfig.ConfigReader[String].emap { string =>
        string
          .split(",")
          .toList
          .traverse(spec => Try(new URI(spec)).toEither)
          .map(URIList.apply)
          .left
          .map(pureconfig.error.ExceptionThrown)
      }
  }
}
