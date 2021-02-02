package com.daddykotex.mrep.file

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Sync
import java.nio.file.Path

trait FS[F[_]] {
  def exists(path: Path): F[Boolean]
}
class FileSystem[F[_]: Sync: ContextShift](blocker: Blocker) extends FS[F] {
  override def exists(path: Path): F[Boolean] = {
    blocker.delay(path.toFile().exists())
  }
}
