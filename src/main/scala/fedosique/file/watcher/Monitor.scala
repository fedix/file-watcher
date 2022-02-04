package fedosique.file.watcher

import cats.effect.Temporal

import scala.concurrent.duration.FiniteDuration
import fs2._

trait Monitor[F[_]] {
  def start(period: FiniteDuration): F[Unit]
}

object Monitor {
  def impl[F[_]: Temporal](synchronizer: Synchronizer[F]): Monitor[F] =
    (period: FiniteDuration) =>
      (synchronizer.synchronize ++ Stream
        .fixedRate(period)
        .debug(_ => "\ncheck for synchronize")
        .flatMap(_ => synchronizer.synchronize)).compile.drain
}
