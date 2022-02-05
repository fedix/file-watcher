package fedosique.file.watcher

import fs2.io.file.Path

import scala.util.control.NoStackTrace

sealed abstract class Error(message: String) extends NoStackTrace

case class WrongNumberOfArguments(n: Int) extends Error(f"Wrong number of arguments: $n instead of 3")

case class PathDoesNotExist(path: Path) extends Error(s"Path $path doesn't exist")

case object PeriodMustBePositive extends Error("Period must be > 0")
case object PeriodMustBeInteger  extends Error("Period must be an integer number")
