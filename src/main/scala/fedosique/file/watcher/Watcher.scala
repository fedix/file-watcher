package fedosique.file.watcher

import cats.{Functor, Monad}
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
        .list(path)
        .evalMap(p => Files[F].getLastModifiedTime(p).map(p -> _))
        .compile
        .toList

    override def filesToUpdate: F[WatchResult] =
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
  }
}
