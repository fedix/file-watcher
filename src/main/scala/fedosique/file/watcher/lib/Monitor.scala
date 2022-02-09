package fedosique.file.watcher.lib

import cats.effect.{Async, Sync, Temporal}
import fs2._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax._

import scala.concurrent.duration.FiniteDuration

trait Monitor[F[_]] {
  def start(period: FiniteDuration): F[Unit]
}

object Monitor {
  def impl[F[_]: Temporal: Logger](synchronizer: Synchronizer[F]): Monitor[F] =
    (period: FiniteDuration) =>
      (synchronizer.synchronize() >> Stream
        .fixedRate(period)
        .evalMap(_ => info"check source")
        .flatMap(_ => synchronizer.synchronize())).compile.drain

  def make[F[_]: Async](synchronizer: Synchronizer[F]): F[Monitor[F]] = {
    import cats.syntax.flatMap._

    Slf4jLogger.create[F].flatMap { implicit l =>
      Sync[F].delay(impl[F](synchronizer))
    }
  }
}
