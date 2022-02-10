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
    import cats.syntax.flatMap._
    import cats.syntax.applicative._

    private def copy(path: Path) = {
      val target = replica / source.relativize(path)
      val copyFile =
        info"replicating $path to $target" >>
          Files[F].copy(path, target, CopyFlags(CopyFlag.ReplaceExisting))

      val copyDir =
        Files[F]
          .exists(target)
          .ifM(
            ().pure,
            info"creating $target" >> Files[F].createDirectory(target)
          )
          .flatTap(_ => info"replicating recursively $path")
          .flatMap(_ => synchronize(path).compile.drain)

      Files[F].isDirectory(path).ifM(copyDir, copyFile)
    }

    private def delete(path: Path) = {
      val deleteFile = info"deleting $path" >> Files[F].delete(path)
      val deleteDir  = info"deleting recursively $path" >> Files[F].deleteRecursively(path)

      Files[F].isDirectory(path).ifM(deleteDir, deleteFile)
    }

    override def synchronize(dir: Path = source): Stream[F, Unit] =
      Stream.eval(info"synchronizing $dir") >>
        Stream
          .eval(watcher.filesToUpdate(dir))
          .evalTap(wr => info"$wr")
          .flatMap(wr =>
            Stream.emits[F, Path](wr.toCopy).mapAsync(4)(copy) ++
              Stream.emits[F, Path](wr.toDelete).mapAsync(4)(delete)
          )
  }

  def make[F[_]: Files: Async](source: Path, replica: Path, watcher: Watcher[F]): F[FileSynchronizer[F]] = {
    import cats.syntax.flatMap._

    Slf4jLogger.create[F].flatMap { implicit l =>
      Sync[F].delay(new FileSynchronizer[F](source, replica, watcher))
    }
  }
}
