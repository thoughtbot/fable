package fable.config

import cats.implicits._
import java.net.URI
import pureconfig.ConfigReader
import scala.util.Try

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
