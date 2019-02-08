# Fable

Fable is a typesafe, functional, Scala API for [Apache Kafka].

Fable provides a Scala/[Typelevel]-friendly layer over the official
kafka-clients library. The interface is intended to map as closely to the
underlying classes as possible while remaining true to the typesafe, functional
principles of Cats, Cats Effect, and fs2.

If you only need to consume or produce Kafka partitions by name and number, you
may want to check out [fs2-kafka], which implements the Kafka API without a
dependency on kafka-clients and provides a simple, clear API for consuming and
producing.

If you're already familiar with kafka-clients, you want consumer groups, or
you'd like control over offsets and commits, this library is for you.

[Apache Kafka]: https://kafka.apache.org/
[Typelevel]: https://typelevel.org/
[fs2-kafka]: https://github.com/Spinoco/fs2-kafka

## Installation

In `build.sbt`:

``` scala
libraryDependencies += "com.thoughtbot" % "fable" %% "0.1.0"
```

## Configuration

Fable is designed to be configured using [HOCON] configuration files. Add your
configuration to `src/main/resources/application.conf`:

```
kafka {
  auto-commit = false
  batch-size = 1024
  client-id = "search"
  max-poll-records = 8094
  polling-timeout = 1 second
  session-timeout = 30 seconds
  uris = "PLAINTEXT://localhost:9092"
}

my-consumer = kafka
my-consumer.topic = "my-topic"
my-consumer.group-id = "my-group"
```

[HOCON]: https://github.com/lightbend/config/blob/master/HOCON.md

## Getting Started

You can load your configuration using [pureconfig]:

``` scala
val config: fable.Config.Consumer =
  pureconfig.loadConfigOrThrow[Config.Consumer]("my-consumer")
```

You can then create a consumer group and start consuming:

``` scala
Consumer.resource[IO, String, String](config).use { consumer =>
  for {
    _ <- consumer.subscribe(fable.Topic("my-topic"))
    records <- consumer.poll
  } yield records
}
```

You can use `records` to get a stream of each batch consumed from Kafka or
`commit` to write the offset back to Kafka:

``` scala
fs2.Stream
  .resource(Consumer.resource[IO, String, String](config))
  .flatMap { consumer =>
    consumer.records.map(_.toSeq).evalMap { records =>
      performIOFromRecords(records) *> consumer.commit
    }
  }
```

See the [Scaladoc] for more details.

[pureconfig]: https://pureconfig.github.io/
[Scaladoc]: https://scaladoc.thoughtbot.com/fable/fable/index.html

## Contributing

Please see [CONTRIBUTING.md](/CONTRIBUTING.md).

## License

Fable is Copyright © 2019 thoughtbot. It is free software, and may be
redistributed under the terms specified in the [LICENSE](/LICENSE) file.

## About

Franz Kafka once wrote a Little Fable:

> "Alas", said the mouse, "the whole world is growing smaller every day. At the
> beginning it was so big that I was afraid, I kept running and running, and I
> was glad when I saw walls far away to the right and left, but these long walls
> have narrowed so quickly that I am in the last chamber already, and there in
> the corner stands the trap that I am running into."
>
> "You only need to change your direction," said the cat, and ate it up.

![thoughtbot](http://presskit.thoughtbot.com/images/thoughtbot-logo-for-readmes.svg)

Fable is maintained and funded by thoughtbot, inc.
The names and logos for thoughtbot are trademarks of thoughtbot, inc.

We love open source software!
See [our other projects][community] or
[hire us][hire] to design, develop, and grow your product.

[community]: https://thoughtbot.com/community?utm_source=github
[hire]: https://thoughtbot.com?utm_source=github
