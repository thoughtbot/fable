package fable

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

/**
  * Value class for topic names.
  */
case class Topic(name: String) extends AnyVal

object Topic {
  implicit val topicConfigReader: ConfigReader[Topic] = deriveReader
}
