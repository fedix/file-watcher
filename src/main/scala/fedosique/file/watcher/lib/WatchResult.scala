package fedosique.file.watcher.lib

import fs2.io.file.Path

case class WatchResult(toCopy: List[Path], toDelete: List[Path])
