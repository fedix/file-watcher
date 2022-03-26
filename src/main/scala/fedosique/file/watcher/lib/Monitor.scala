package fedosique.file.watcher.lib

import cats.effect.kernel.Resource.ExitCase
import cats.effect.{Concurrent, Async, Temporal}
import cats.implicits._
import fs2._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax._

import scala.concurrent.duration.FiniteDuration

object Monitor {
  private def apply[F[_]: Temporal: Concurrent: Logger](
      synchronizer: Synchronizer[F],
      period: FiniteDuration
  ): F[Unit] = {
    val stream = synchronizer.synchronize() ++ Stream
      .fixedRate(period)
      .evalMap(_ => info"start new synchronization")
      .flatMap(_ => synchronizer.synchronize())
      .onFinalizeCase {
        case ExitCase.Succeeded  => info"Monitor stopped"
        case ExitCase.Errored(e) => error"Monitor errored: ${e.getMessage}"
        case ExitCase.Canceled   => warn"Monitor canceled"
      }

    stream.compile.drain
  }

  def start[F[_]: Async](synchronizer: Synchronizer[F], period: FiniteDuration): F[Unit] =
    Slf4jLogger
      .create[F]
      .flatMap(implicit l => Monitor(synchronizer, period))
}
