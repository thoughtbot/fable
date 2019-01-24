package fable

import cats.effect.IO
import org.scalatest.AsyncFunSuite
import scala.concurrent.ExecutionContext

class KafkaSpec extends AsyncFunSuite {
  test("group") {
    implicit val contextShift = IO.contextShift(implicitly[ExecutionContext])
    val config = TestConfig.kafka.copy(prefix = Some("example."))
    val kafka = new Kafka[IO](config)
    val group = kafka.group("test")

    assert(group.name === "example.test")
  }

  test("topic") {
    implicit val contextShift = IO.contextShift(implicitly[ExecutionContext])
    val config = TestConfig.kafka.copy(prefix = Some("example."))
    val kafka = new Kafka[IO](config)
    val topic = kafka.topic("test")

    assert(topic.name === "example.test")
  }
}
