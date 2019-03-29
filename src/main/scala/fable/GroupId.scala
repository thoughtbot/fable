package fable

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

/**
  * Value class for consumer group IDs.
  */
case class GroupId(name: String) extends AnyVal

object GroupId {
  implicit val groupIdConfigReader: ConfigReader[GroupId] = deriveReader
}
