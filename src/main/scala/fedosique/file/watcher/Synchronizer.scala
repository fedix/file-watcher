package fedosique.file.watcher

import cats.Monad
import cats.effect.kernel.Concurrent
import fs2.io.file.{CopyFlag, CopyFlags, Files, Path}
import fs2._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

import scala.concurrent.duration.FiniteDuration

trait Synchronizer[F[_]] {
  def synchronize: Stream[F, Unit]
}

object Synchronizer {
  class FileSynchronizer[F[_]: Files: Concurrent: Monad: Logger](source: Path, replica: Path, watcher: Watcher[F])
      extends Synchronizer[F] {
    private def updatePipe: Pipe[F, WatchResult, Unit] =
      _.flatMap(r => Stream.emits(r.toUpdate))
        .evalTap(p => info"replicating ${p.fileName} to ${replica / p.fileName}")
        .mapAsync(4) { p =>
          Files[F].copy(p, replica / p.fileName, CopyFlags(CopyFlag.ReplaceExisting))
        }

    private def deletePipe: Pipe[F, WatchResult, Unit] =
      _.flatMap(r => Stream.emits(r.toDelete))
        .evalTap(p => info"deleting $p")
        .mapAsync(4)(Files[F].delete)

    override def synchronize: Stream[F, Unit] =
      Stream
        .eval(watcher.filesToUpdate)
        .broadcastThrough(updatePipe, deletePipe)
  }

  def make[F[_]: Files: Concurrent: Monad: Logger](source: Path, replica: Path, watcher: Watcher[F]) =
    new FileSynchronizer[F](source, replica, watcher)
}
