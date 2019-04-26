package fable.config

import cats.effect.Sync
import cats.implicits._
import fable.{Deserializer, GroupId}
import java.util.Properties
import org.apache.kafka.clients.consumer.ConsumerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader
import pureconfig.module.squants._
import scala.concurrent.duration.FiniteDuration
import squants.information.Information

/**
  * Configuration options for constructor a Kafka [[Consumer]].
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
  *     consumer {
  *       auto-commit = false
  *       auto-offset-reset = "earliest"
  *       batch-size = 1024
  *       client-id = "fable-test"
  *       group-id = "fable-test"
  *       max-poll-records = 1024
  *       polling-timeout = 1 second
  *       session-timeout = 30 seconds
  *       uris = "PLAINTEXT://localhost:9092"
  *     }
  *   }
  *
  *   // Main.scala
  *   val config: fable.Config.Consumer =
  *     pureconfig.loadConfigOrThrow[fable.Config.Consumer]("kafka.consumer")
  * }}}
  *
  * @see
  * [[https://kafka.apache.org/21/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
  * ConsumerConfig]] for details on consumer configuration.
  *
  * @constructor
  * @param autoCommit whether to automatically commit the previous offset
  * @param autoOffsetReset what to do when there is no initial offset in
  * Kafka or if the current offset does not exist any more on the server
  * @param clientCertificate SSL certificate used to authenticate the client
  * @param clientId identifier for tracking which client is making requests
  * @param clientKey SSL private key used to authenticate the client
  * @param fetchMaxSize the maximum data size to fetch in a single poll
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
    autoOffsetReset: AutoOffsetReset,
    clientCertificate: Option[String],
    clientId: String,
    clientKey: Option[String],
    fetchMaxSize: Option[Information],
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

    new Common(clientCertificate,
               clientKey,
               sslIdentificationAlgorithm,
               trustedCertificate,
               uris).addProperties(result) *>
      addConsumerProperties[F](result) *>
      addDeserializationProperties[F, K, V](result) *>
      Sync[F].pure(result)
  }

  private def addConsumerProperties[F[_]: Sync](target: Properties): F[Unit] =
    Sync[F].delay {
      target.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      target.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId)
      target.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit.toString)
      target.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                 new Integer(maxPollRecords))

      fetchMaxSize
        .map(_.toBytes.toInt.toString)
        .foreach(target.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, _))

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

  private def addDeserializationProperties[F[_]: Sync,
                                           K: Deserializer,
                                           V: Deserializer](
      target: Properties) = Sync[F].delay {
    target.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
               Deserializer[K].instance.getClass.getName)
    target.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
               Deserializer[V].instance.getClass.getName)
  }

}

object Consumer {
  implicit val consumerConfigReader: ConfigReader[Consumer] = deriveReader
}
