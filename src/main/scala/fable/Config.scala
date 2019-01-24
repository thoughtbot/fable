package fable

import cats.implicits._
import java.net.URI
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig;
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Config {
  case class Kafka(prefix: Option[String], uris: URIList) {
    def properties: Properties = {
      val result = new Properties()
      result.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                 uris.bootstrapServers)
      result.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, uris.scheme)
      result
    }

    def prefix[A](name: String)(f: String => A): A =
      f(s"${prefix.getOrElse("")}$name")
  }

  case class Consumer(
      autoCommit: Boolean,
      groupId: Option[Group],
      maxPollRecords: Int,
      pollingTimeout: FiniteDuration,
      sessionTimeout: FiniteDuration,
      clientId: String
  ) {
    def properties[K: Deserializer, V: Deserializer](
        kafka: Kafka): Properties = {
      val result = kafka.properties
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
    implicit val uriListReader: pureconfig.ConfigReader[URIList] =
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
