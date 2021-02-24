package com.daddykotex.mrep.file

import cats.effect._
import java.nio.file.Path

trait FS[F[_]] {
  def exists(path: Path): F[Boolean]
  def mkdirs(path: Path): F[Path]
}
class FileSystem[F[_]: Sync: ContextShift](blocker: Blocker) extends FS[F] {
  override def exists(path: Path): F[Boolean] = {
    blocker.delay(path.toFile().exists())
  }
  override def mkdirs(path: Path): F[Path] = {
    fs2.io.file.createDirectories(blocker, path)
  }
}
