package com.daddykotex.mrep.git

import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import com.daddykotex.mrep.proc._
import java.nio.file.Path

final case class Repository(directory: Path) {
  implicit def ops[F[_]: Monad](implicit exec: Exec[F, Command]): RepositoryOps[F] = new GitRepositoryOps(
    RepoGitCli.forOne(this, exec)
  )
}

trait RepositoryOps[F[_]] {
  def isClean(): F[Boolean]
  def wipeRepository(): F[Unit]
  def newBranchFromMaster(target: String): F[Unit]
  def updateStage(): F[Unit]
  def commit(messages: NonEmptyList[String]): F[Unit]
  def forcePush(branch: String): F[Unit]
  def hasDiffFromMaster(target: String): F[Boolean]
}

class GitRepositoryOps[F[_]: Monad](git: RepoGitCli[F]) extends RepositoryOps[F] {

  override def forcePush(branch: String): F[Unit] = {
    git.push(force = true, FullBranch("origin", branch))
  }

  def updateStage(): F[Unit] = {
    git.add(update = true)
  }

  def commit(messages: NonEmptyList[String]): F[Unit] = {
    git.commit(messages)
  }

  def isClean(): F[Boolean] = {
    git.status(untrackedFiles = UntrackedFiles.No).map(_.isEmpty)
  }

  def wipeRepository(): F[Unit] = {
    for {
      _ <- git.reset(hard = true, FullBranch("origin", "master"))
      _ <- git.clean(force = true, recursive = true)
    } yield ()
  }

  def hasDiffFromMaster(target: String): F[Boolean] = {
    val diff = git.diff(target, "master", quiet = false).map(lines => !lines.forall(_.trim.nonEmpty))
    val status = git.status(UntrackedFiles.All).map(_.nonEmpty)
    status.flatMap(b1 => diff.tupleLeft(b1)).map { case (untracked, diff) => diff || untracked }
  }

  def newBranchFromMaster(target: String): F[Unit] = {
    for {
      _ <- git.checkout(newBranch = CheckoutBranch.ForceNewBranch, target, Some(FullBranch("origin", "master")))
      _ <- git.pull()
    } yield ()
  }
}
