package com.daddykotex.mrep.git

import cats.Applicative
import cats.implicits._
import com.daddykotex.mrep.proc._
import java.nio.file.Path

final case class Repository(directory: Path) {
  implicit def ops[F[_]: Applicative](implicit exec: Exec[F, Command]): RepositoryOps[F] = new RepositoryOps(
    RepoGitCli.forOne(this, exec)
  )
}

class RepositoryOps[F[_]: Applicative](git: RepoGitCli[F]) {
  def isClean(): F[Boolean] = {
    git.status(untrackedFiles = UntrackedFiles.No).map(_.isEmpty)
  }
}
