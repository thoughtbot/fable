# Fable

Functional API for Kafka using Cats, Cats Effect, and fs2.

## Installation

In `build.sbt`:

    libraryDependencies += "com.thoughtbot" % "fable" %% "0.1.0"

## Synopsis

    import cats.implicits._
    import cats.effect._
    import fable._
    import pureconfig.generic.auto._

    object Main extends IOApp {
      def run(args: List[String]): IO[ExitCode] = {
        val config: Config.Consumer =
          pureconfig.loadConfigOrThrow[Config.Consumer]("kafka.my-consumer")
        Consumer.resource[IO, String, String](consumerConfig).use { consumer =>
          for {
            _ <- consumer.subscribe(kafka.topic("my-topic"))
            records <- consumer.poll
            _ <- IO.delay(println(s"Consumed \${records.count} records"))
          } yield ExitCode.Success
        }
      }
    }

See the [Scaladoc] for more details.

## Contributing

Please see [CONTRIBUTING.md](/CONTRIBUTING.md).

## License

Fable is Copyright © 2019 thoughtbot. It is free software, and may be
redistributed under the terms specified in the [LICENSE](/LICENSE) file.

[Scaladoc]: https://scaladoc.thoughtbot.com/fable/fable/index.html
