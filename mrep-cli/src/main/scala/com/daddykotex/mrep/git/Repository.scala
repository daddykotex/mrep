package com.daddykotex.mrep.git

import cats.Monad
import cats.implicits._
import com.daddykotex.mrep.proc._
import java.nio.file.Path

final case class Repository(directory: Path) {
  implicit def ops[F[_]: Monad](implicit exec: Exec[F, Command]): RepositoryOps[F] = new RepositoryOps(
    RepoGitCli.forOne(this, exec)
  )
}

class RepositoryOps[F[_]: Monad](git: RepoGitCli[F]) {
  def isClean(): F[Boolean] = {
    git.status(untrackedFiles = UntrackedFiles.No).map(_.isEmpty)
  }

  def wipeRepository(): F[Unit] = {
    for {
      _ <- git.reset(hard = true, FullBranch("origin", "master"))
      _ <- git.clean(force = true, recursive = true)
    } yield ()
  }

  def newBranchFromMaster(target: String): F[Unit] = {
    for {
      _ <- git.checkout(newBranch = CheckoutBranch.ForceNewBranch, target, Some(FullBranch("origin", "master")))
      _ <- git.pull()
    } yield ()
  }
}
