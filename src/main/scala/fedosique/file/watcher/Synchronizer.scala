package fedosique.file.watcher

import cats.Monad
import cats.effect.kernel.Concurrent
import fs2.io.file.{CopyFlag, CopyFlags, Files, Path}
import fs2._

import scala.concurrent.duration.FiniteDuration

trait Synchronizer[F[_]] {
  def synchronize: Stream[F, Unit]
}

object Synchronizer {
  class FileSynchronizer[F[_]: Files: Concurrent: Monad](source: Path, replica: Path) extends Synchronizer[F] {
    import cats.syntax.all._

    private def listFiles(path: Path): F[List[(Path, FiniteDuration)]] =
      Files[F]
        .list(path)
        .evalMap(p => Files[F].getLastModifiedTime(p).map(p -> _))
        .compile
        .toList

    private def filesToUpdate = {
      for {
        sourceFiles  <- listFiles(source)
        replicaFiles <- listFiles(replica)
      } yield {
        val replicaFileNames = replicaFiles.map { case (p, _) => p.fileName }

        println(sourceFiles.map { case (p, _) => p.fileName })
        println(replicaFileNames)

        sourceFiles.collect {
          case (sourcePath, _) if !replicaFileNames.contains(sourcePath.fileName) =>
            println(s"${sourcePath.fileName} doesn't exist in $replica")
            Some(sourcePath)
          case (sourcePath, sourceTime) =>
            replicaFiles
              .find { case (replicaPath, replicaTime) =>
                replicaPath.fileName == sourcePath.fileName && sourceTime > replicaTime
              }
              .map(_._1)
        }.flatten
      }
    }

    override def synchronize: Stream[F, Unit] =
      Stream
        .evalSeq(filesToUpdate)
        .debug(p => s"synchronizing ${p.fileName} to ${replica / p.fileName}")
        .evalMap { p =>
          Files[F].copy(p, replica / p.fileName, CopyFlags(CopyFlag.ReplaceExisting))
        }
  }

  def make[F[_]: Files: Concurrent: Monad](source: Path, replica: Path) =
    new FileSynchronizer[F](source, replica)
}
