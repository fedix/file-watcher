package fedosique.file.watcher

import cats.effect._
import fs2.io.file.Path
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
//TODO: verify args
// deletion
// copy replaceExisting
// custom error type
// add watcher
object Main extends IOApp {
  def program(source: Path, replica: Path, period: FiniteDuration): IO[Unit] =
    Slf4jLogger.create[IO].flatMap { implicit l =>
      val synchronizer = Synchronizer.make[IO](source, replica)
      val monitor      = Monitor.impl(synchronizer)

      info"starting application" *> monitor.start(period)
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
