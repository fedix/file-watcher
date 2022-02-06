package fedosique.file.watcher.cli

import fs2.io.file.Path

import scala.concurrent.duration.FiniteDuration

case class Args(source: Path, replica: Path, period: FiniteDuration)
