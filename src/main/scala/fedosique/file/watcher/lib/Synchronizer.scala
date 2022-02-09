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

    private def copyFiles(paths: List[Path]) =
      Stream
        .emits[F, Path](paths)
        .evalTap(p => info"replicating $p to ${replica / source.relativize(p)}")
        .mapAsync(4)(p => Files[F].copy(p, replica / source.relativize(p), CopyFlags(CopyFlag.ReplaceExisting)))

    private def copyDirs(paths: List[Path]) =
      Stream
        .emits[F, Path](paths)
        .mapAsync(4)(dir =>
          Files[F]
            .exists(replica / source.relativize(dir))
            .ifM(
              ().pure,
              info"creating${replica / source.relativize(dir)}" >>
                Files[F].createDirectory(replica / source.relativize(dir))
            )
            .as(dir)
        )
        .evalTap(dir => info"replicating recursively $dir")
        .flatMap(dir => synchronize(dir))

    private def deleteFiles(paths: List[Path]) =
      Stream
        .emits[F, Path](paths)
        .evalTap(p => info"deleting $p")
        .mapAsync(4)(p => Files[F].delete(p))

    private def deleteDirs(paths: List[Path]) =
      Stream
        .emits[F, Path](paths)
        .evalTap(p => info"deleting recursively $p")
        .mapAsync(4)(p => Files[F].deleteRecursively(p))

    type ListTuple[A] = (List[A], List[A])

    private def separate[A](seq: List[A], condF: A => F[Boolean]): F[ListTuple[A]] =
      seq.foldLeft((List.empty[A], List.empty[A]).pure) { (lists, a) =>
        lists.flatMap { case (l, r) =>
          condF(a).map { cond =>
            if (cond) (a :: l, r)
            else (l, a :: r)
          }
        }
      }

    override def synchronize(dir: Path = source): Stream[F, Unit] =
      Stream.eval(info"synchronizing $dir") >>
        Stream
          .eval(watcher.filesToUpdate(dir))
          .evalTap(wr => info"$wr")
          .evalTap { wr =>
            (for {
              (dirs, files) <- Stream.eval(separate(wr.toCopy, Files[F].isDirectory))
              _             <- copyFiles(files) ++ copyDirs(dirs)
            } yield ()).compile.drain
          }
          .evalTap { wr =>
            (for {
              (dirs, files) <- Stream.eval(separate(wr.toDelete, Files[F].isDirectory))
              _             <- deleteFiles(files) ++ deleteDirs(dirs)
            } yield ()).compile.drain
          }
          .void
  }

  def make[F[_]: Files: Async](source: Path, replica: Path, watcher: Watcher[F]): F[FileSynchronizer[F]] = {
    import cats.syntax.flatMap._

    Slf4jLogger.create[F].flatMap { implicit l =>
      Sync[F].delay(new FileSynchronizer[F](source, replica, watcher))
    }
  }
}
