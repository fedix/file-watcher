package fedosique.file.watcher

import fs2.io.file.Path

sealed abstract class Error(message: String) extends Exception(message)

case class WrongNumberOfArguments(n: Int) extends Error(f"$n arguments passed instead of 3")

case class PathDoesNotExist(path: Path) extends Error(s"Path $path doesn't exist")

case object NonPositivePeriod extends Error("Period must be > 0")
case object NonIntegerPeriod  extends Error("Period must be an integer number")
