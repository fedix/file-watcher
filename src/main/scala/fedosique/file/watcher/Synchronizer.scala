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

        sourceFiles.collect {
          case (sourcePath, _) if !replicaFileNames.contains(sourcePath.fileName) =>
            Some(sourcePath)
          case (sourcePath, sourceTime) =>
            replicaFiles
              .find { case (replicaPath, replicaTime) =>
                replicaPath.fileName == sourcePath.fileName && sourceTime > replicaTime
              }
              .map(_._1)
        }.flatten
      }

    override def synchronize: Stream[F, Unit] =
      Stream
        .evalSeq(filesToUpdate)
        .evalTap(p => info"replicating ${p.fileName} to ${replica / p.fileName}")
        .evalMap { p =>
          Files[F].copy(p, replica / p.fileName, CopyFlags(CopyFlag.ReplaceExisting))
        }
  }

  def make[F[_]: Files: Concurrent: Monad: Logger](source: Path, replica: Path) =
    new FileSynchronizer[F](source, replica)
}
