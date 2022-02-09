package fedosique.file.watcher.lib

import cats.effect.kernel.Concurrent
import cats.effect.{Async, Sync}
import fs2._
import fs2.io.file.{CopyFlag, CopyFlags, Files, Path}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax._

trait Synchronizer[F[_]] {
  def source: Path
  def synchronize(dir: Path = source): Stream[F, Unit]
}

object Synchronizer {
  class FileSynchronizer[F[_]: Files: Concurrent: Logger](override val source: Path, replica: Path, watcher: Watcher[F])
      extends Synchronizer[F] {
    import cats.syntax.functor._
    import cats.syntax.flatMap._
    import cats.syntax.applicative._

    private def copy(path: Path) = {
      def copyFile(path: Path) =
        info"replicating $path to ${replica / source.relativize(path)}" >>
          Files[F].copy(path, replica / source.relativize(path), CopyFlags(CopyFlag.ReplaceExisting))

      def copyDir(dir: Path) =
        Files[F]
          .exists(replica / source.relativize(dir))
          .ifM(
            ().pure,
            info"creating${replica / source.relativize(dir)}" >>
              Files[F].createDirectory(replica / source.relativize(dir))
          )
          .flatTap(_ => info"replicating recursively $dir")
          .flatMap(_ => synchronize(dir).compile.drain)

      Files[F].isDirectory(path).ifM(copyDir(path), copyFile(path))
    }

    private def delete(path: Path) = {
      def deleteFile(path: Path) =
        info"deleting $path" >> Files[F].delete(path)

      def deleteDir(path: Path) =
        info"deleting recursively $path" >> Files[F].deleteRecursively(path)

      Files[F].isDirectory(path).ifM(deleteDir(path), deleteFile(path))
    }

    override def synchronize(dir: Path = source): Stream[F, Unit] =
      Stream.eval(info"synchronizing $dir") >>
        Stream
          .eval(watcher.filesToUpdate(dir))
          .evalTap(wr => info"$wr")
          .evalTap(wr =>
            (Stream.emits[F, Path](wr.toCopy).mapAsync(4)(copy) ++
              Stream.emits[F, Path](wr.toDelete).mapAsync(4)(delete)).compile.drain
          )
          .void
  }

  def make[F[_]: Files: Async](source: Path, replica: Path, watcher: Watcher[F]): F[FileSynchronizer[F]] = {
    import cats.syntax.flatMap._

    Slf4jLogger.create[F].flatMap { implicit l =>
      Sync[F].delay(new FileSynchronizer[F](source, replica, watcher))
    }
  }
}
