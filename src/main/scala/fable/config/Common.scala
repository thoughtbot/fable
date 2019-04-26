package fable.config

import cats.effect.Sync
import cats.implicits._
import com.heroku.sdk.BasicKeyStore
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SslConfigs

private[fable] class Common(
    clientCertificate: Option[String],
    clientKey: Option[String],
    sslIdentificationAlgorithm: Option[String],
    trustedCertificate: Option[String],
    uris: URIList
) {
  def addProperties[F[_]: Sync](target: Properties): F[Unit] =
    addCommonProperties(target) *>
      addTrustStoreProperties[F](target) *>
      addClientStoreProperties[F](target)

  protected def addCommonProperties[F[_]: Sync](target: Properties): F[Unit] =
    Sync[F].delay {
      target.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                 uris.bootstrapServers)
      target.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, uris.scheme)
      target.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
                 sslIdentificationAlgorithm.getOrElse(""))
    }

  protected def addTrustStoreProperties[F[_]: Sync](
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

  protected def addClientStoreProperties[F[_]: Sync](
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
