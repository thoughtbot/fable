package fable

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

/**
  * Value class for topic names.
  */
case class Topic private (name: String) extends AnyVal

object Topic {
  private[fable] def apply(name: String): Topic = new Topic(name)

  implicit val topicConfigReader: ConfigReader[Topic] = deriveReader
}
