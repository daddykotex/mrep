package com.daddykotex.mrep.git

import cats.Applicative
import cats.implicits._
import io.github.vigoo.prox.ProxFS2
import java.nio.file.Path

final case class Repository(directory: Path) {
  implicit def ops[F[_]: Applicative](implicit prox: ProxFS2[F]): RepositoryOps[F] = new RepositoryOps(
    GitCli.forOne(this, prox)
  )
}

class RepositoryOps[F[_]: Applicative](git: GitCli[F]) {
  def isClean(): F[Boolean] = {
    git.status(untrackedFiles = UntrackedFiles.No).map(_.isEmpty)
  }
}
