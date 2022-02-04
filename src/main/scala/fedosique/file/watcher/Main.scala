package fedosique.file.watcher

import cats.effect._
import fs2.io.file.Path

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
//TODO: verify args
// logging
// deletion
// custom error type
object Main extends IOApp {
  def program(source: Path, replica: Path, period: FiniteDuration): IO[Unit] = {
    val synchronizer = Synchronizer.make[IO](source, replica)
    Monitor
      .impl[IO](synchronizer)
      .start(period)
  }

  def verifyArgs(args: List[String]): Either[String, List[String]] = ???

  override def run(args: List[String]): IO[ExitCode] = args match {
    case source :: replica :: period :: Nil =>
      program(
        Path(source),
        Path(replica),
        FiniteDuration(period.toLong, TimeUnit.SECONDS)
      ).as(ExitCode.Success)

    case _ => IO.println("Not all required args are passed").as(ExitCode.Error)
  }
}
