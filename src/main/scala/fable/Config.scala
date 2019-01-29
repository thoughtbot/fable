package fable

import cats.implicits._
import java.net.URI
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
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
    * @param clientId identifier for tracking which client is making requests
    * @param groupId identifier for the consumer group this consumer will join
    * each time [[Consumer.poll]] is invoked.
    * @param maxPollRecords the maximum number of records to return each time
    * [[Consumer.poll]] is invoked.
    * @param pollingTimeout how long to wait before giving up when
    * [[Consumer.poll]] is invoked.
    * @param sessionTimeout how long to wait before assuming a failure has
    * occurred when using a consumer group
    * @param uris bootstrap URIs used to connect to a Kafka cluster.
    */
  case class Consumer(
      autoCommit: Boolean,
      clientId: String,
      groupId: Option[GroupId],
      maxPollRecords: Int,
      pollingTimeout: FiniteDuration,
      sessionTimeout: FiniteDuration,
      uris: URIList
  ) {
    def properties[K: Deserializer, V: Deserializer]: Properties = {
      val result = new Properties()
      result.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                 uris.bootstrapServers)
      result.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, uris.scheme)
      result.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      result.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId)
      result.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit.toString)
      result.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                 Deserializer[K].instance.getClass.getName)
      result.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                 new Integer(maxPollRecords))
      result.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                 sessionTimeout.toMillis.toString)
      result.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                 Deserializer[V].instance.getClass.getName)
      groupId
        .map(_.name)
        .foreach(
          result.put(ConsumerConfig.GROUP_ID_CONFIG, _)
        )
      result
    }
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
      if (needKeys)
        "SSL"
      else
        "PLAINTEXT"

    def needKeys: Boolean =
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
