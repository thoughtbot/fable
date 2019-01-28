package fable

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

/**
  * Value class for consumer group IDs.
  *
  * @see [[Kafka.groupId]]
  */
case class GroupId private (name: String) extends AnyVal

object GroupId {
  implicit val groupIdConfigReader: ConfigReader[GroupId] = deriveReader
}