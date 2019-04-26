package fable

import cats.effect.IO
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.AsyncFunSuite
import org.apache.kafka.clients.producer.MockProducer
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

class ProducerSpec extends AsyncFunSuite {
  implicit val contextShift = IO.contextShift(implicitly[ExecutionContext])

  test("send") {
    val topic = Topic("fable-test-example")
    val mockProducer = createMockProducer
    val record = ProducerRecord[String, String](topic, "one", "1")
    val producer = new Producer[IO, String, String](config, mockProducer)

    (for {
      _ <- producer.send(record)
      records <- IO.delay { mockProducer.history.asScala }
    } yield {
      assert(records.map(_.key) === Seq("one"))
      assert(records.map(_.value) === Seq("1"))
    }).unsafeToFuture
  }

  val config = TestConfig.producer

  private def createMockProducer: MockProducer[String, String] = {
    new MockProducer[String, String](true,
                                     new StringSerializer,
                                     new StringSerializer)
  }
}
