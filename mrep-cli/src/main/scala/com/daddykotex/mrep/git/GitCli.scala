package com.daddykotex.mrep.git

import cats.Applicative
import cats.data.NonEmptyList
import cats.implicits._
import com.daddykotex.mrep.proc._
import java.nio.file.Path
import java.nio.file.Paths

sealed trait CheckoutBranch {
  import CheckoutBranch._
  val arg: String = this match {
    case NewBranch      => "-b"
    case ForceNewBranch => "-B"
  }
}
object CheckoutBranch {
  case object NewBranch extends CheckoutBranch
  case object ForceNewBranch extends CheckoutBranch
}

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

final case class FullBranch(remote: String, branch: String) {
  def asString: String = s"$remote/$branch"
}

trait RepoGitCli[F[_]] {
  val repo: Repository

  def status(untrackedFiles: UntrackedFiles): F[Vector[Path]]
  def reset(hard: Boolean, target: FullBranch): F[Unit]
  def clean(force: Boolean, recursive: Boolean): F[Unit]
  def checkout(newBranch: CheckoutBranch, target: String, startPoint: Option[FullBranch]): F[Unit]
  def pull(): F[Unit]
  def push(force: Boolean, target: FullBranch): F[Unit]
  def add(update: Boolean, files: Paths*): F[Unit]
  def commit(messages: NonEmptyList[String]): F[Unit]
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

    override def reset(hard: Boolean, target: FullBranch): F[Unit] = {
      val hardFlag = if (hard) { List("--hard") }
      else { List.empty }
      val command = Command("git", List("reset") ++ hardFlag ++ List(target.asString))
      exec.runVoid(command, Some(repo.directory))
    }

    def clean(force: Boolean, recursive: Boolean): F[Unit] = {
      val forceFlag = if (force) List("--force") else List.empty
      val recursiveFlag = if (recursive) List("--d") else List.empty
      val command = Command("git", List("clean") ++ forceFlag ++ recursiveFlag)
      exec.runVoid(command, Some(repo.directory))
    }

    def checkout(newBranch: CheckoutBranch, target: String, startPoint: Option[FullBranch]): F[Unit] = {
      val newBranchFlag = List(newBranch.arg)
      val startPointArg = startPoint.map(_.asString).toList
      val command = Command("git", List("checkout") ++ newBranchFlag ++ List(target) ++ startPointArg)
      exec.runVoid(command, Some(repo.directory))
    }

    def pull(): F[Unit] = {
      val command = Command("git", List("pull"))
      exec.runVoid(command, Some(repo.directory))
    }

    def add(update: Boolean, files: Paths*): F[Unit] = {
      val updateFlag = if (update) List("--update") else List.empty
      val filesArg = files.map(_.toString()).toList
      val command = Command("git", List("add") ++ updateFlag ++ filesArg)
      exec.runVoid(command, Some(repo.directory))
    }

    def commit(messages: NonEmptyList[String]): F[Unit] = {
      val messageOpts = messages.flatMap(message => NonEmptyList.of("--message", message)).toList
      val command = Command("git", List("commit") ++ messageOpts)
      exec.runVoid(command, Some(repo.directory))
    }

    def push(force: Boolean, target: FullBranch): F[Unit] = {
      val forceFlag = if (force) List("--force") else List.empty
      val command = Command("git", List("push") ++ forceFlag ++ List(target.remote, target.branch))
      exec.runVoid(command, Some(repo.directory))
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
