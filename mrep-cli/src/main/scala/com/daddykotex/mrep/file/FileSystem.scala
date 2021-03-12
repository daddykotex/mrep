package com.daddykotex.mrep.file

import cats.effect._
import java.nio.file.Path

trait FileSystem[F[_]] {
  def exists(path: Path): F[Boolean]
  def mkdirs(path: Path): F[Path]
}

object FileSystem {
  def forSync[F[_]: Sync: ContextShift](blocker: Blocker): FileSystem[F] = new FileSystem[F]() {
    def exists(path: Path): F[Boolean] = {
      blocker.delay(path.toFile().exists())
    }
    def mkdirs(path: Path): F[Path] = {
      fs2.io.file.createDirectories(blocker, path)
    }
  }
}
