package fable.config

import pureconfig.ConfigReader

/**
  * Config for resetting offsets when no offset has been recorded for a consumer
  * in a consumer group.
  */
sealed trait AutoOffsetReset {
  def renderString: String
}

object AutoOffsetReset {
  case object Earliest extends AutoOffsetReset {
    val renderString = "earliest"
  }

  case object Latest extends AutoOffsetReset {
    val renderString = "latest"
  }

  case object None extends AutoOffsetReset {
    val renderString = "none"
  }

  implicit val autoOffsetResetReader: ConfigReader[AutoOffsetReset] =
    pureconfig.ConfigReader[String].emap { string =>
      List(Earliest, Latest, None)
        .filter(_.renderString == string)
        .headOption
        .toRight(pureconfig.error
          .CannotConvert(string, "auto offset", "Unkown value"))
    }
}
