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

case class WatchResult(toUpdate: List[Path], toDelete: List[Path])

object Synchronizer {
  class FileSynchronizer[F[_]: Files: Concurrent: Monad: Logger](source: Path, replica: Path) extends Synchronizer[F] {
    import cats.syntax.all._

    private def listFiles(path: Path): F[List[(Path, FiniteDuration)]] =
      Files[F]
        .list(path)
        .evalMap(p => Files[F].getLastModifiedTime(p).map(p -> _))
        .compile
        .toList

    private def filesToUpdate =
      for {
        sourceFiles  <- listFiles(source)
        replicaFiles <- listFiles(replica)
      } yield {
        val replicaFileNames = replicaFiles.map { case (p, _) => p.fileName }
        val sourceFileNames  = sourceFiles.map { case (p, _) => p.fileName }

        val createdOrUpdated = sourceFiles.collect {
          case (sourcePath, _) if !replicaFileNames.contains(sourcePath.fileName) =>
            Some(sourcePath)
          case (sourcePath, sourceTime) =>
            replicaFiles
              .find { case (replicaPath, replicaTime) =>
                replicaPath.fileName == sourcePath.fileName && sourceTime > replicaTime
              }
              .map(_._1)
        }.flatten

        val deleted = replicaFiles.collect {
          case (replicatedFile, _) if !sourceFileNames.contains(replicatedFile.fileName) =>
            Some(replicatedFile)
        }.flatten

        WatchResult(createdOrUpdated, deleted)
      }

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
        .eval(filesToUpdate)
        .broadcastThrough(updatePipe, deletePipe)
  }

  def make[F[_]: Files: Concurrent: Monad: Logger](source: Path, replica: Path) =
    new FileSynchronizer[F](source, replica)
}
