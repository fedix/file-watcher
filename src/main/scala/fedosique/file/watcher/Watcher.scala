package fedosique.file.watcher

import cats.Monad
import cats.effect.kernel.Concurrent
import fs2.io.file.{Files, Path}

import scala.concurrent.duration.FiniteDuration

trait Watcher[F[_]] {
  def filesToUpdate: F[WatchResult]
}

object Watcher {
  import cats.syntax.functor._
  import cats.syntax.flatMap._

  def impl[F[_]: Monad: Concurrent: Files](source: Path, replica: Path): Watcher[F] = new Watcher[F] {
    private def listFiles(path: Path): F[List[(Path, FiniteDuration)]] =
      Files[F]
        .walk(path)
        .evalMap(p => Files[F].getLastModifiedTime(p).map(p -> _))
        .compile
        .toList

    private def findCreatedOrUpdated(
        sourceFiles: List[(Path, FiniteDuration)],
        replicaFiles: List[(Path, FiniteDuration)]
    ) = {
      val replicaFileNames = replicaFiles.map { case (p, _) => replica.relativize(p) }

      sourceFiles.collect {
        case (sourceFile, _) if !replicaFileNames.contains(source.relativize(sourceFile)) =>
          Some(sourceFile)

        case (sourceFile, sourceTime) =>
          replicaFiles
            .find { case (replicaFile, replicaTime) =>
              replica.relativize(replicaFile) == source.relativize(sourceFile) && sourceTime > replicaTime
            }
            .as(sourceFile)

      }.flatten
    }

    private def findDeleted(sourceFiles: List[(Path, FiniteDuration)], replicaFiles: List[(Path, FiniteDuration)]) = {
      val sourceFileNames = sourceFiles.map { case (p, _) => source.relativize(p) }

      replicaFiles.collect {
        case (replicatedFile, _) if !sourceFileNames.contains(replica.relativize(replicatedFile)) =>
          Some(replicatedFile)
      }.flatten
    }

    override def filesToUpdate: F[WatchResult] =
      for {
        sourceFiles  <- listFiles(source)
        replicaFiles <- listFiles(replica)
      } yield WatchResult(
        findCreatedOrUpdated(sourceFiles, replicaFiles),
        findDeleted(sourceFiles, replicaFiles)
      )
  }
}
