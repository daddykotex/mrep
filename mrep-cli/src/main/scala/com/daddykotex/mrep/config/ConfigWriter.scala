package com.daddykotex.mrep.config

import io.circe.syntax._
import java.nio.file.Path
import cats.effect.Blocker
import cats.effect.Sync
import cats.effect.ContextShift

class ConfigWriter[F[_]: Sync: ContextShift](path: Path, blocker: Blocker) {
  def toConfigFile(): fs2.Pipe[F, RepoConfig, Path] = { s =>
    s
      .map(_.asJson.spaces2)
      .through(fs2.text.utf8Encode)
      .through(fs2.io.file.writeAll(path, blocker))
      .map(_ => path)
  }
}
