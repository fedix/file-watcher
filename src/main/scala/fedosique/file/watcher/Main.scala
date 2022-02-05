package fedosique.file.watcher

import cats.effect._
import fs2.io.file.Path
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
//TODO: verify args
// copy replaceExisting
// custom error type
// add logging to file
object Main extends IOApp {
  def program(source: Path, replica: Path, period: FiniteDuration): IO[Unit] = {
    val watcher = Watcher.impl[IO](source, replica)
    for {
      logger       <- Slf4jLogger.create[IO]
      synchronizer <- Synchronizer.make(source, replica, watcher)
      monitor      <- Monitor.make(synchronizer)
      _            <- logger.info("starting application")
      _            <- monitor.start(period)
    } yield ()
  }

  def verifyArgs(args: List[String]): Either[String, List[String]] = ???

  override def run(args: List[String]): IO[ExitCode] = args match {
    case source :: replica :: period :: Nil =>
      program(
        Path(source),
        Path(replica),
        FiniteDuration(period.toLong, TimeUnit.SECONDS)
      ).as(ExitCode.Success)

    case _ => IO.raiseError(new Exception("Not all required args are passed")).as(ExitCode.Error)
  }
}
