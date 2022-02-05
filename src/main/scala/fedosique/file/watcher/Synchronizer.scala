package fedosique.file.watcher

import cats.effect.{Async, Sync}
import cats.effect.kernel.Concurrent
import fs2.io.file.{CopyFlag, CopyFlags, Files, Path}
import fs2._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax._

trait Synchronizer[F[_]] {
  def synchronize: Stream[F, Unit]
}

object Synchronizer {
  class FileSynchronizer[F[_]: Files: Concurrent: Logger](replica: Path, watcher: Watcher[F]) extends Synchronizer[F] {
    private def updatePipe: Pipe[F, WatchResult, Unit] =
      _.flatMap(r => Stream.emits(r.toCopy))
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

  def make[F[_]: Files: Async](replica: Path, watcher: Watcher[F]): F[FileSynchronizer[F]] = {
    import cats.syntax.flatMap._

    Slf4jLogger.create[F].flatMap { implicit l =>
      Sync[F].delay(new FileSynchronizer[F](replica, watcher))
    }
  }
}
