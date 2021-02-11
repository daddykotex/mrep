package com.daddykotex.mrep.git

import cats.Applicative
import cats.implicits._
import com.daddykotex.proc.Command
import com.daddykotex.proc.Exec
import java.nio.file.Path
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

trait GitCli[F[_]] {
  val repo: Repository

  def status(untrackedFiles: UntrackedFiles): F[Vector[Path]]
}

object GitCli {
  def forOne[F[_]: Applicative](repository: Repository, exec: Exec[F, Command]): GitCli[F] = new GitCli[F] {
    val repo: Repository = repository

    def status(untrackedFiles: UntrackedFiles): F[Vector[Path]] = {
      val command = Command("git", List("status", s"--untracked-files=${untrackedFiles.arg}", "--porcelain"))
      exec
        .runLines(command, repo.directory)
        .map(_.map(line => Paths.get(line)))
    }
  }
}
