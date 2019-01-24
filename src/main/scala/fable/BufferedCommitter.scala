package fable

import cats.effect.{ContextShift, Sync}
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.{Chunk, Sink, Stream}

private[fable] class BufferedCommitter[F[_]: ContextShift: Sync, K, V](
    batchSize: Long,
    consumer: Consumer[F, K, V],
    sink: Sink[F, ConsumerRecord[K, V]]) {
  def run: F[Unit] =
    for {
      state <- Ref.of[F, State](newState)
      _ <- consumer.records
        .evalMap(receivedRecords(state, _))
        .compile
        .drain
    } yield ()

  private def receivedRecords(ref: Ref[F, State],
                              records: ConsumerRecords[K, V]): F[Unit] =
    for {
      _ <- ref.update(_.receive(records))
      state <- ref.get
      _ <- flush(ref).whenA(state.isReady)
    } yield ()

  private def flush(ref: Ref[F, State]): F[Unit] =
    for {
      state <- ref.get
      _ <- Stream.chunk(state.toChunk).to(sink).compile.drain
      _ <- consumer.commit
      _ <- ref.set(newState)
    } yield ()

  private case class State(queue: Chunk.Queue[ConsumerRecord[K, V]],
                           caughtUp: Boolean) {
    def receive(records: ConsumerRecords[K, V]): State =
      State(queue :+ Chunk.seq(records.toSeq), records.isEmpty)

    def isReady: Boolean =
      isQueueFull || hasLastRecords

    def toChunk: Chunk[ConsumerRecord[K, V]] =
      queue.toChunk

    private def isQueueFull: Boolean =
      queue.size >= batchSize

    private def isQueueNonEmpty: Boolean =
      queue.size > 0

    private def hasLastRecords: Boolean =
      caughtUp && isQueueNonEmpty
  }

  private def newState: State =
    State(queue = Chunk.Queue.empty[ConsumerRecord[K, V]], caughtUp = false)
}
