package fable

import cats.Eval
import cats.effect.IO
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.producer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.AsyncFunSuite
import org.apache.kafka.clients.consumer.{MockConsumer, OffsetResetStrategy}
import scala.concurrent.ExecutionContext
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._

class ConsumerSpec extends AsyncFunSuite {
  implicit val contextShift = IO.contextShift(implicitly[ExecutionContext])

  test("poll") {
    val topic = Topic("fable-test-example")
    val offset = 1.toLong
    val topicPartition: TopicPartition = new TopicPartition(topic.name, 1)
    val mockConsumer = createMockConsumer(offset, topicPartition)
    val record = createRecordMock(mockConsumer,
                                  topic,
                                  topicPartition,
                                  (offset, "one", "1"))
    val consumer = new Consumer[IO, String, String](config, mockConsumer)

    (for {
      records <- consumer.poll
    } yield {
      assert(records.toSeq.head.key === "one")
      assert(records.toSeq.head.value === "1")
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

  test("assignment") {
    val first = Topic("fable-test-one")
    val second = Topic("fable-test-two")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(first.name)
      _ <- createTopic(second.name)
      partitions <- consumer.use { instance =>
        for {
          _ <- instance.subscribe(first)
          _ <- instance.poll
          result <- instance.assignment
        } yield result
      }
    } yield {
      assert(partitions.map(_.topic).distinct === List(first))
    }).unsafeToFuture
  }

  test("position") {
    val topic = Topic("fable-test")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(topic.name)
      position <- consumer.use { instance =>
        for {
          _ <- instance.subscribe(topic)
          _ <- instance.poll
          partitions <- instance.assignment
          result <- instance.position(partitions.head)
        } yield result
      }
    } yield {
      assert(position >= 0)
    }).unsafeToFuture
  }

  test("endOffsets") {
    val topic = Topic("fable-test")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(topic.name)
      offsets <- consumer.use { instance =>
        for {
          _ <- instance.subscribe(topic)
          _ <- instance.poll
          partitions <- instance.assignment
          result <- instance.endOffsets(partitions)
        } yield result
      }
    } yield {
      assert(offsets.keys.toList === List(Partition(topic, 0)))
    }).unsafeToFuture
  }

  test("listTopics") {
    val fableTopic = Topic("fable-test")
    val consumer = Consumer.resource[IO, String, String](config)

    (for {
      _ <- createTopic(fableTopic.name)
      topicsAndPartitions <- consumer.use { instance =>
        for {
          _ <- instance.subscribe(fableTopic)
          _ <- instance.poll
          topics <- instance.listTopics
          partitions <- instance.partitionsFor(fableTopic)
        } yield (topics, partitions)
      }
    } yield {
      val topic = topicsAndPartitions._1.find(t => {
        t._1 == fableTopic
      })
      assert(topic === Some(fableTopic -> topicsAndPartitions._2.toList))
    }).unsafeToFuture
  }

  test("seek") {
    val topic = Topic("fable-test-example")
    val firstOffset = 1.toLong
    val secondOffset = 2.toLong
    val partition = 1
    val topicPartition: TopicPartition =
      new TopicPartition(topic.name, partition)
    val mockConsumer = createMockConsumer(firstOffset, topicPartition)
    createRecordMock(mockConsumer,
                     topic,
                     topicPartition,
                     (firstOffset, "1", "First"),
                     (secondOffset, "2", "Second"))
    val consumer = new Consumer[IO, String, String](config, mockConsumer)

    (for {
      _ <- consumer.subscribe(topic)
      _ <- consumer.seek(Partition(topic, partition), secondOffset)
      records <- consumer.poll
    } yield {
      assert(records.toSeq.map(_.value) === List("Second"))
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
      properties.put(producer.ProducerConfig.ACKS_CONFIG, "all")
      properties.put(producer.ProducerConfig.CLIENT_ID_CONFIG, "fable-test")
      properties.put(producer.ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                     new Integer(5000))
      properties.put(producer.ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                     new Integer(4000))
      properties.put(producer.ProducerConfig.BATCH_SIZE_CONFIG,
                     new Integer(records.length))
      properties.put(producer.ProducerConfig.LINGER_MS_CONFIG, new Integer(0))
      properties.put(producer.ProducerConfig.BUFFER_MEMORY_CONFIG,
                     new Integer(1024))
      properties.put(producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                     classOf[StringSerializer].getName)
      properties.put(producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                     classOf[StringSerializer].getName)

      val instance = new producer.KafkaProducer[String, String](properties)

      for ((key, value) <- records) {
        instance.send(
          new producer.ProducerRecord[String, String](topic, key, value))
      }

      instance.close
    }

  private def createMockConsumer(
      offset: Long,
      topicPartition: TopicPartition): MockConsumer[String, String] = {
    val mockConsumer =
      new MockConsumer[String, String](OffsetResetStrategy.EARLIEST)
    val partitionMap =
      Map(topicPartition -> offset.asInstanceOf[java.lang.Long]).asJava

    mockConsumer.subscribe(Seq(topicPartition.topic).asJava)
    mockConsumer.rebalance(ArrayBuffer(topicPartition).asJava)
    mockConsumer.updateBeginningOffsets(partitionMap)
    mockConsumer.updateEndOffsets(partitionMap)

    mockConsumer
  }

  private def createRecordMock(mockConsumer: MockConsumer[String, String],
                               topic: Topic,
                               topicPartition: TopicPartition,
                               records: (Long, String, String)*): Unit = {
    for (record <- records) {
      mockConsumer.addRecord(
        new org.apache.kafka.clients.consumer.ConsumerRecord(
          topic.name,
          topicPartition.partition,
          record._1,
          record._2,
          record._3
        )
      )
    }
  }

  val config = TestConfig.consumer
}
