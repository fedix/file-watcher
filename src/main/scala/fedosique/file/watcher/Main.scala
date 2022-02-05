package fedosique.file.watcher

import cats.effect._
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxFlatMapOps, toTraverseOps}
import fs2.io.file.{Files, Path}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object Main extends IOApp {
  def verifyArgs(args: List[String]): IO[Args] =
    args match {
      case source :: replica :: period :: Nil =>
        val verifyPaths: IO[List[Path]] =
          List(source, replica)
            .map(Path(_))
            .traverse(path => Files[IO].exists(path).ifM(IO(path), IO.raiseError(PathDoesNotExist(path))))

        val verifyPeriod = period.toLongOption match {
          case Some(number) if number > 0 => FiniteDuration(number, TimeUnit.SECONDS).pure[IO]
          case Some(_)                    => IO.raiseError(PeriodMustBePositive)
          case None                       => IO.raiseError(PeriodMustBeInteger)
        }

        for {
          paths          <- verifyPaths
          verifiedPeriod <- verifyPeriod
        } yield Args(paths.head, paths.last, verifiedPeriod)

      case _ => IO.raiseError(WrongNumberOfArguments(args.length))
    }

  def program(args: Args): IO[Unit] = {
    val watcher = Watcher.impl[IO](args.source, args.replica)
    for {
      logger       <- Slf4jLogger.create[IO]
      synchronizer <- Synchronizer.make(args.source, args.replica, watcher)
      monitor      <- Monitor.make(synchronizer)
      _            <- logger.info("starting application")
      _            <- monitor.start(args.period)
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] =
    (verifyArgs(args) >>= program).as(ExitCode.Success)
}
