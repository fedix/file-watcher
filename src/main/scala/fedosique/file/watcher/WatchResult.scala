package fedosique.file.watcher

import fs2.io.file.Path

case class WatchResult(toUpdate: List[Path], toDelete: List[Path])
