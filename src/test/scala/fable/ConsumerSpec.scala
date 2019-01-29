package fable

import cats.Eval
import cats.effect.IO
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.producer.{
  KafkaProducer,
  ProducerConfig,
  ProducerRecord
}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.AsyncFunSuite
import scala.concurrent.ExecutionContext

class ConsumerSpec extends AsyncFunSuite {
  implicit val contextShift = IO.contextShift(implicitly[ExecutionContext])

  test("poll") {
    val topic = Topic("fable-test-example")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(topic.name)
      _ <- sendRecords(topic.name, "one" -> "1", "two" -> "2")
      records <- consumer.use { instance =>
        for {
          _ <- instance.subscribe(topic)
          records <- instance.poll
        } yield records.toSeq
      }
    } yield {
      assert(
        records.map(record => (record.key, record.value)) === Seq("one" -> "1",
                                                                  "two" -> "2"))
    }).unsafeToFuture
  }

  test("commit") {
    val topic = Topic("fable-test-example")
    val consumer = Eval.always {
      Consumer.resource[IO, String, String](config)
    }

    (for {
      _ <- createTopic(topic.name)
      _ <- sendRecords(topic.name, "one" -> "1", "two" -> "2")
      _ <- consumer.value.use { instance =>
        for {
          _ <- instance.subscribe(topic)
          records <- instance.poll
          _ <- instance.commit
        } yield records.toSeq
      }
      _ <- sendRecords(topic.name, "three" -> "3", "four" -> "4")
      records <- consumer.value.use { instance =>
        for {
          _ <- instance.subscribe(topic)
          records <- instance.poll
        } yield records.toSeq
      }
    } yield {
      assert(
        records.map(record => (record.key, record.value)) === Seq(
          "three" -> "3",
          "four" -> "4"))
    }).unsafeToFuture
  }

  test("records") {
    val topic = Topic("fable-test-example")
    val consumer =
      Consumer.resource[IO, String, String](config.copy(maxPollRecords = 2))

    (for {
      _ <- createTopic(topic.name)
      _ <- sendRecords(topic.name, "one" -> "1", "two" -> "2")
      _ <- sendRecords(topic.name, "three" -> "3")
      records <- consumer.use { instance =>
        for {
          _ <- instance.subscribe(topic)
          records <- instance.records
            .map(_.toSeq.map(_.value))
            .take(2)
            .compile
            .toList
        } yield records
      }
    } yield {
      assert(records === List(Seq("1", "2"), Seq("3")))
    }).unsafeToFuture
  }

  test("partitionsFor") {
    val topic = Topic("fable-test-example")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(topic.name)
      partitions <- consumer.use(_.partitionsFor(topic))
    } yield {
      assert(partitions === Seq(Partition(topic, 0)))
    }).unsafeToFuture
  }

  test("assign") {
    val first = Topic("fable-test-one")
    val second = Topic("fable-test-two")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(first.name)
      _ <- createTopic(second.name)
      _ <- sendRecords(first.name, "one" -> "1")
      _ <- sendRecords(second.name, "two" -> "2")
      records <- consumer.use { instance =>
        for {
          firstPartitions <- instance.partitionsFor(first)
          _ <- instance.assign(firstPartitions)
          records <- instance.poll
        } yield records.toSeq
      }
    } yield {
      assert(records.map(_.value) === Seq("1"))
    }).unsafeToFuture
  }

  private def createTopic(topic: String): IO[Unit] =
    IO.delay {
      val properties = new Properties
      properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                     config.uris.bootstrapServers)
      properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                     config.uris.scheme)
      val adminClient = AdminClient.create(properties)
      val newTopic = new NewTopic(topic, 1, 1)

      adminClient.deleteTopics(java.util.Collections.singletonList(topic))
      adminClient.createTopics(java.util.Collections.singletonList(newTopic))

      adminClient.close
    }

  private def sendRecords(topic: String, records: (String, String)*): IO[Unit] =
    IO.delay {
      val properties = new Properties
      properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                     config.uris.bootstrapServers)
      properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                     config.uris.scheme)
      properties.put(ProducerConfig.ACKS_CONFIG, "all")
      properties.put(ProducerConfig.CLIENT_ID_CONFIG, "fable-test")
      properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                     new Integer(5000))
      properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                     new Integer(4000))
      properties.put(ProducerConfig.BATCH_SIZE_CONFIG,
                     new Integer(records.length))
      properties.put(ProducerConfig.LINGER_MS_CONFIG, new Integer(0))
      properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, new Integer(1024))
      properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                     classOf[StringSerializer].getName)
      properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                     classOf[StringSerializer].getName)

      val producer = new KafkaProducer[String, String](properties)

      for ((key, value) <- records) {
        producer.send(new ProducerRecord[String, String](topic, key, value))
      }

      producer.close
    }

  val config = TestConfig.consumer
}
