package com.daddykotex.mrep.git

import cats.Applicative
import cats.implicits._
import java.nio.file.Path
import com.daddykotex.mrep.proc._
import java.nio.file.Paths

sealed trait UntrackedFiles {
  import UntrackedFiles._
  val arg: String = this match {
    case No     => "no"
    case Normal => "normal"
    case All    => "all"
  }
}
object UntrackedFiles {
  case object No extends UntrackedFiles
  case object Normal extends UntrackedFiles
  case object All extends UntrackedFiles
}

trait RepoGitCli[F[_]] {
  val repo: Repository

  def status(untrackedFiles: UntrackedFiles): F[Vector[Path]]
}

object RepoGitCli {
  def forOne[F[_]: Applicative](repository: Repository, exec: Exec[F, Command]): RepoGitCli[F] = new RepoGitCli[F] {
    val repo: Repository = repository

    def status(untrackedFiles: UntrackedFiles): F[Vector[Path]] = {
      val command = Command("git", List("status", s"--untracked-files=${untrackedFiles.arg}", "--porcelain"))
      exec
        .runLines(command, Some(repo.directory))
        .map(_.map(line => Paths.get(line)))
    }
  }
}

trait GitCli[F[_]] {
  def clone(gitUrl: String, target: Path): F[Path]
}

object GitCli {
  def apply[F[_]: Applicative](exec: Exec[F, Command]): GitCli[F] = new GitCli[F] {
    override def clone(gitUrl: String, target: Path): F[Path] = {
      val command = Command("git", List("clone", gitUrl, target.toString()))
      exec
        .runVoid(command, None)
        .as(target)
    }
  }

  object ExpectedErrors {
    def ignoreExistingRepositories: PartialFunction[Throwable, Unit] = {
      case Exec.Error(err) if err.getMessage().contains("already exists and is not an empty directory") => ()
    }
  }
}
